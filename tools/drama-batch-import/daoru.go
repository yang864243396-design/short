package main

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
)

// DaoruStore keeps JSONL state keyed by dir name.
type DaoruStore struct {
	byDir map[string]*Record
}

func NewDaoruStore() *DaoruStore {
	return &DaoruStore{byDir: make(map[string]*Record)}
}

func (s *DaoruStore) Get(dir string) *Record {
	return s.byDir[dir]
}

func (s *DaoruStore) Set(r *Record) {
	s.byDir[r.Dir] = r
}

func (s *DaoruStore) Has(dir string) bool {
	_, ok := s.byDir[dir]
	return ok
}

func (s *DaoruStore) All() []*Record {
	out := make([]*Record, 0, len(s.byDir))
	for _, r := range s.byDir {
		out = append(out, r)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Dir < out[j].Dir })
	return out
}

func LoadDaoru(path string) (*DaoruStore, error) {
	st := NewDaoruStore()
	f, err := os.Open(path)
	if err != nil {
		if os.IsNotExist(err) {
			return st, nil
		}
		return nil, err
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	// large lines
	buf := make([]byte, 0, 1024*64)
	sc.Buffer(buf, 1024*1024)
	lineNo := 0
	for sc.Scan() {
		lineNo++
		line := bytes.TrimSpace(sc.Bytes())
		if len(line) == 0 {
			continue
		}
		var r Record
		if err := json.Unmarshal(line, &r); err != nil {
			return nil, fmt.Errorf("daoru.txt line %d: %w", lineNo, err)
		}
		if r.Dir == "" {
			return nil, fmt.Errorf("daoru.txt line %d: missing dir", lineNo)
		}
		st.byDir[r.Dir] = &r
	}
	return st, sc.Err()
}

func (s *DaoruStore) SaveAtomic(path string) error {
	var buf bytes.Buffer
	keys := make([]string, 0, len(s.byDir))
	for k := range s.byDir {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for _, k := range keys {
		r := s.byDir[k]
		b, err := json.Marshal(r)
		if err != nil {
			return err
		}
		buf.Write(b)
		buf.WriteByte('\n')
	}
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, buf.Bytes(), 0644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}
