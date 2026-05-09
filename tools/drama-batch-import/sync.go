package main

import (
	"fmt"
	"io"
	"os"
	pathpkg "path/filepath"
	"sort"
	"strings"
)

// §7.1：剧集根下直接子目录名为 1、2、4、5 的整树不进入 staging（分集仅用 3/*.mp4）。
func importSkipRootDir(name string) bool {
	switch name {
	case "1", "2", "4", "5":
		return true
	default:
		return false
	}
}

// importRelUsesSkippedRoot 若 rel 相对剧集根的第一段为排除名则 true（仅首段，故 misc/1 不排）。
func importRelUsesSkippedRoot(rel string) bool {
	rel = pathpkg.ToSlash(rel)
	if rel == "." || rel == "" {
		return false
	}
	first, _, _ := strings.Cut(rel, "/")
	return importSkipRootDir(first)
}

func removeImportSkippedRootsFromStaging(stagingRootDir string) {
	ent, err := os.ReadDir(stagingRootDir)
	if err != nil {
		return
	}
	for _, e := range ent {
		if importSkipRootDir(e.Name()) {
			_ = os.RemoveAll(pathpkg.Join(stagingRootDir, e.Name()))
		}
	}
}

func pruneStaging(srcRootDir, stagingRootDir string) error {
	if _, err := os.Stat(srcRootDir); err != nil {
		return fmt.Errorf("source dir: %w", err)
	}
	_ = os.MkdirAll(stagingRootDir, 0755)

	removeImportSkippedRootsFromStaging(stagingRootDir)

	var paths []string
	_ = pathpkg.Walk(stagingRootDir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if path == stagingRootDir {
			return nil
		}
		paths = append(paths, path)
		return nil
	})
	sort.Slice(paths, func(i, j int) bool { return len(paths[i]) > len(paths[j]) })

	for _, p := range paths {
		rel, err := pathpkg.Rel(stagingRootDir, p)
		if err != nil {
			continue
		}
		if importRelUsesSkippedRoot(rel) {
			_ = os.RemoveAll(p)
			continue
		}
		srcPath := pathpkg.Join(srcRootDir, rel)
		if _, err := os.Stat(srcPath); os.IsNotExist(err) {
			_ = os.RemoveAll(p)
		}
	}
	return nil
}

func copyTreeIncremental(srcRootDir, stagingRootDir string) error {
	return pathpkg.Walk(srcRootDir, func(srcPath string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		rel, err := pathpkg.Rel(srcRootDir, srcPath)
		if err != nil {
			return err
		}
		if importRelUsesSkippedRoot(rel) {
			if info.IsDir() {
				return pathpkg.SkipDir
			}
			return nil
		}
		if info.IsDir() {
			return nil
		}
		dstPath := pathpkg.Join(stagingRootDir, rel)
		if err := os.MkdirAll(pathpkg.Dir(dstPath), 0755); err != nil {
			return err
		}
		if st, err := os.Stat(dstPath); err == nil {
			if st.Size() == info.Size() && st.ModTime().Equal(info.ModTime()) {
				return nil
			}
		}
		if err := copyFile(srcPath, dstPath); err != nil {
			return err
		}
		return os.Chtimes(dstPath, info.ModTime(), info.ModTime())
	})
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer func() { _ = out.Close() }()
	if _, err := io.Copy(out, in); err != nil {
		return err
	}
	return out.Sync()
}

func removeDirAllIfExists(path string) error {
	if path == "" || path == "/" {
		return fmt.Errorf("refusing to remove unsafe path %q", path)
	}
	if _, err := os.Stat(path); os.IsNotExist(err) {
		return nil
	}
	return os.RemoveAll(path)
}
