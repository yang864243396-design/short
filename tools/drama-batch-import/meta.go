package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	pathpkg "path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

var episodeFileRe = regexp.MustCompile(`^第(\d+)集_.+\.mp4$`)

// MetaVideo carries drama fields from meta.json.
type MetaVideo struct {
	Title       string          `json:"title"`
	VideoDesc   string          `json:"videoDesc"`
	Categories  json.RawMessage `json:"categories"`
	PlayCnt     FlexibleInt64   `json:"playCnt"`
	SeriesID    FlexibleString  `json:"seriesId"`
}

type MetaFile struct {
	Video MetaVideo `json:"video"`
}

type FlexibleInt64 int64

func (f *FlexibleInt64) UnmarshalJSON(b []byte) error {
	b = bytesTrim(b)
	if len(b) == 0 || string(b) == "null" {
		*f = 0
		return nil
	}
	if b[0] == '"' {
		var s string
		if err := json.Unmarshal(b, &s); err != nil {
			return err
		}
		v, err := strconv.ParseInt(strings.TrimSpace(s), 10, 64)
		if err != nil {
			return err
		}
		*f = FlexibleInt64(v)
		return nil
	}
	var v int64
	if err := json.Unmarshal(b, &v); err != nil {
		return err
	}
	*f = FlexibleInt64(v)
	return nil
}

type FlexibleString string

func (f *FlexibleString) UnmarshalJSON(b []byte) error {
	b = bytesTrim(b)
	if len(b) == 0 || string(b) == "null" {
		*f = ""
		return nil
	}
	if b[0] == '"' {
		var s string
		if err := json.Unmarshal(b, &s); err != nil {
			return err
		}
		*f = FlexibleString(s)
		return nil
	}
	var n json.Number
	if err := json.Unmarshal(b, &n); err == nil {
		*f = FlexibleString(n.String())
		return nil
	}
	var i int64
	if err := json.Unmarshal(b, &i); err != nil {
		return err
	}
	*f = FlexibleString(strconv.FormatInt(i, 10))
	return nil
}

func bytesTrim(b []byte) []byte {
	return bytes.TrimSpace(b)
}

// ParseCategoryNames handles nested JSON string array per spec §8.1.
func ParseCategoryNames(raw json.RawMessage) ([]string, error) {
	if len(raw) == 0 || string(raw) == "null" {
		return nil, nil
	}
	b := raw
	// If outer is JSON string, unwrap once
	if len(b) >= 2 && b[0] == '"' {
		var s string
		if err := json.Unmarshal(b, &s); err != nil {
			return nil, err
		}
		b = []byte(s)
	}
	var arr []string
	if err := json.Unmarshal(b, &arr); err != nil {
		return nil, fmt.Errorf("categories: %w", err)
	}
	return arr, nil
}

func JoinCategories(names []string) string {
	return strings.Join(names, ",")
}

// EpisodeFile is one deduped playable mp4 under staging/<dir>/3/.
type EpisodeFile struct {
	N        int
	BaseName string
	FullPath string
}

func ListDedupedEpisodes(stagingDir string) ([]EpisodeFile, error) {
	dir3 := pathpkg.Join(stagingDir, "3")
	ent, err := os.ReadDir(dir3)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	type pair struct {
		n    int
		base string
		path string
	}
	var pairs []pair
	for _, e := range ent {
		if e.IsDir() {
			continue
		}
		name := e.Name()
		if !strings.HasSuffix(name, ".mp4") {
			continue
		}
		m := episodeFileRe.FindStringSubmatch(name)
		if m == nil {
			continue
		}
		n, _ := strconv.Atoi(m[1])
		pairs = append(pairs, pair{n: n, base: name, path: pathpkg.Join(dir3, name)})
	}
	sort.Slice(pairs, func(i, j int) bool {
		if pairs[i].n != pairs[j].n {
			return pairs[i].n < pairs[j].n
		}
		return pairs[i].base < pairs[j].base
	})
	var out []EpisodeFile
	seen := map[int]bool{}
	for _, p := range pairs {
		if seen[p.n] {
			continue
		}
		seen[p.n] = true
		out = append(out, EpisodeFile{N: p.n, BaseName: p.base, FullPath: p.path})
	}
	return out, nil
}

func ParseMetaJSON(path string) (*MetaFile, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var m MetaFile
	if err := json.Unmarshal(b, &m); err != nil {
		return nil, err
	}
	return &m, nil
}
