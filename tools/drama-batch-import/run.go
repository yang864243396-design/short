package main

import (
	"fmt"
	"os"
	pathpkg "path/filepath"
	"sort"
	"strings"
)

// Run executes one import pass per DRAMA_BATCH_IMPORT_SPEC.md.
func Run(cfg *Config, client *Client) error {
	lockPath := pathpkg.Join(pathpkg.Dir(cfg.StateFile), ".daoru_import.lock")
	unlock, err := acquireLock(lockPath)
	if err != nil {
		return fmt.Errorf("lock %s: %w", lockPath, err)
	}
	defer unlock()

	store, err := LoadDaoru(cfg.StateFile)
	if err != nil {
		return fmt.Errorf("load daoru: %w", err)
	}

	for _, r := range store.All() {
		if r.Status != statusDone {
			continue
		}
		st := pathpkg.Join(cfg.StagingRoot, r.Dir)
		if err := removeDirAllIfExists(st); err != nil {
			fmt.Fprintf(os.Stderr, "[drama-import] warn: completed staging rm %s: %v\n", st, err)
			r.LastError = fmt.Sprintf("staging cleanup: %v", err)
			_ = persist(store, cfg.StateFile, r)
		}
	}

	candidates, err := listCandidateDirs(cfg)
	if err != nil {
		return err
	}

	var completedThisRun []string
	advanced := 0

	for _, dirName := range candidates {
		rec := store.Get(dirName)
		if rec != nil && rec.Status == statusDone {
			continue
		}
		if cfg.MaxDirsPerRun != nil && advanced >= *cfg.MaxDirsPerRun {
			break
		}
		if err := processDirectory(cfg, client, store, dirName, &completedThisRun); err != nil {
			return err
		}
		advanced++
	}

	planned := "unlimited"
	if cfg.MaxDirsPerRun != nil {
		planned = fmt.Sprintf("%d", *cfg.MaxDirsPerRun)
	}
	reason := "queue_exhausted"
	if cfg.MaxDirsPerRun != nil && advanced >= *cfg.MaxDirsPerRun {
		reason = "quota_reached"
	}
	fmt.Fprintf(os.Stderr, "[drama-import] summary: planned_max=%s advanced=%d exit_reason=%s\n", planned, advanced, reason)

	for _, d := range completedThisRun {
		st := pathpkg.Join(cfg.StagingRoot, d)
		if err := removeDirAllIfExists(st); err != nil {
			fmt.Fprintf(os.Stderr, "[drama-import] warn: exit staging rm %s: %v\n", st, err)
		}
	}

	return nil
}

func listCandidateDirs(cfg *Config) ([]string, error) {
	ent, err := os.ReadDir(cfg.SourceRoot)
	if err != nil {
		return nil, fmt.Errorf("read source_root: %w", err)
	}
	var names []string
	for _, e := range ent {
		if !e.IsDir() {
			continue
		}
		name := e.Name()
		if name == "." || name == ".." {
			continue
		}
		info, err := e.Info()
		if err != nil {
			continue
		}
		if cfg.ModTimeBefore != nil && !info.ModTime().Before(*cfg.ModTimeBefore) {
			continue
		}
		names = append(names, name)
	}
	sort.Strings(names)
	return names, nil
}

func persist(store *DaoruStore, statePath string, rec *Record) error {
	rec.touch()
	store.Set(rec)
	return store.SaveAtomic(statePath)
}

func processDirectory(cfg *Config, client *Client, store *DaoruStore, dirName string, completed *[]string) error {
	persisted := store.Has(dirName)
	rec := store.Get(dirName)
	if rec == nil {
		rec = &Record{Dir: dirName, Status: statusCopyPartial}
	}
	if rec.Status == statusDone {
		return nil
	}

	srcDir := pathpkg.Join(cfg.SourceRoot, dirName)
	stDir := pathpkg.Join(cfg.StagingRoot, dirName)

	if err := pruneStaging(srcDir, stDir); err != nil {
		fmt.Fprintf(os.Stderr, "[drama-import] %s prune: %v\n", dirName, err)
		rec.Status = statusCopyPartial
		rec.LastError = err.Error()
		_ = persist(store, cfg.StateFile, rec)
		return nil
	}
	if err := copyTreeIncremental(srcDir, stDir); err != nil {
		fmt.Fprintf(os.Stderr, "[drama-import] %s copy: %v\n", dirName, err)
		rec.Status = statusCopyPartial
		rec.LastError = err.Error()
		_ = persist(store, cfg.StateFile, rec)
		return nil
	}

	eps, err := ListDedupedEpisodes(stDir)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[drama-import] %s list episodes: %v\n", dirName, err)
		rec.Status = statusAPIError
		rec.LastError = err.Error()
		_ = persist(store, cfg.StateFile, rec)
		return nil
	}
	if len(eps) == 0 {
		fmt.Fprintf(os.Stderr, "[drama-import] skip %s: no valid .mp4 under 3/ (pattern 第N集_*.mp4)\n", dirName)
		if persisted {
			rec.LastError = "no valid mp4 under 3/"
			_ = persist(store, cfg.StateFile, rec)
		}
		return nil
	}

	rec.Status = statusFullCopy
	rec.LastError = ""
	if err := persist(store, cfg.StateFile, rec); err != nil {
		return err
	}

	metaPath := pathpkg.Join(stDir, "meta.json")
	meta, err := ParseMetaJSON(metaPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[drama-import] %s meta.json: %v\n", dirName, err)
		rec.Status = statusAPIError
		rec.LastError = fmt.Sprintf("meta.json: %v", err)
		_ = persist(store, cfg.StateFile, rec)
		return nil
	}

	sid := strings.TrimSpace(string(meta.Video.SeriesID))
	if sid != "" && sid != dirName && !rec.SeriesIDWarned {
		fmt.Fprintf(os.Stderr, "[drama-import] WARN %s: meta.seriesId=%q != dir name %q\n", dirName, sid, dirName)
		rec.SeriesIDWarned = true
		if err := persist(store, cfg.StateFile, rec); err != nil {
			return err
		}
	}

	catNames, err := ParseCategoryNames(meta.Video.Categories)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[drama-import] %s categories: %v\n", dirName, err)
		rec.Status = statusAPIError
		rec.LastError = err.Error()
		_ = persist(store, cfg.StateFile, rec)
		return nil
	}
	categoryJoined, err := client.EnsureCategoryNames(catNames)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[drama-import] %s ensure categories: %v\n", dirName, err)
		rec.Status = statusAPIError
		rec.LastError = err.Error()
		_ = persist(store, cfg.StateFile, rec)
		return nil
	}

	coverPath := pathpkg.Join(stDir, "cover.jpg")
	if strings.TrimSpace(rec.CoverURL) == "" {
		if _, err := os.Stat(coverPath); err != nil {
			rec.Status = statusAPIError
			rec.LastError = "cover.jpg missing"
			_ = persist(store, cfg.StateFile, rec)
			return nil
		}
		url, err := client.UploadImage(coverPath)
		if err != nil {
			fmt.Fprintf(os.Stderr, "[drama-import] %s upload cover: %v\n", dirName, err)
			rec.Status = statusAPIError
			rec.LastError = err.Error()
			_ = persist(store, cfg.StateFile, rec)
			return nil
		}
		rec.CoverURL = url
		if err := persist(store, cfg.StateFile, rec); err != nil {
			return err
		}
	}

	if rec.DramaID == 0 {
		id, err := client.CreateDrama(
			meta.Video.Title,
			meta.Video.VideoDesc,
			categoryJoined,
			rec.CoverURL,
			len(eps),
			int64(meta.Video.PlayCnt),
		)
		if err != nil {
			fmt.Fprintf(os.Stderr, "[drama-import] %s create drama: %v\n", dirName, err)
			rec.Status = statusAPIError
			rec.LastError = err.Error()
			_ = persist(store, cfg.StateFile, rec)
			return nil
		}
		rec.DramaID = id
		if err := persist(store, cfg.StateFile, rec); err != nil {
			return err
		}
	}

	for _, ep := range eps {
		if ep.N <= rec.LastOkEpisode {
			continue
		}
		vpath, err := client.UploadVideo(ep.FullPath)
		if err != nil {
			fmt.Fprintf(os.Stderr, "[drama-import] %s upload ep %d: %v\n", dirName, ep.N, err)
			rec.Status = statusAPIError
			rec.LastError = err.Error()
			_ = persist(store, cfg.StateFile, rec)
			return nil
		}
		isFree := ep.N <= 10
		unlockCoins := 10
		if isFree {
			unlockCoins = 0
		}
		title := fmt.Sprintf("第%d集", ep.N)
		err = client.CreateEpisode(rec.DramaID, ep.N, title, vpath, isFree, unlockCoins)
		if err != nil {
			if isDuplicateEpisodeErr(err) {
				rec.LastOkEpisode = ep.N
				if err := persist(store, cfg.StateFile, rec); err != nil {
					return err
				}
				continue
			}
			fmt.Fprintf(os.Stderr, "[drama-import] %s create episode %d: %v\n", dirName, ep.N, err)
			rec.Status = statusAPIError
			rec.LastError = err.Error()
			_ = persist(store, cfg.StateFile, rec)
			return nil
		}
		rec.LastOkEpisode = ep.N
		if err := persist(store, cfg.StateFile, rec); err != nil {
			return err
		}
	}

	rec.Status = statusDone
	rec.LastError = ""
	if err := persist(store, cfg.StateFile, rec); err != nil {
		return err
	}
	*completed = append(*completed, dirName)
	fmt.Fprintf(os.Stderr, "[drama-import] done %s drama_id=%d episodes=%d\n", dirName, rec.DramaID, len(eps))
	return nil
}
