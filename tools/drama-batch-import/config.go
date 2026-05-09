package main

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	statusCopyPartial = "拷贝部分"
	statusFullCopy    = "全拷贝"
	statusAPIError    = "接口部分报错"
	statusDone        = "已完成"

	maxDirsUpperBound = 10000
)

// Config holds CLI + env resolved settings.
type Config struct {
	MaxDirsPerRun   *int
	ModTimeBefore   *time.Time // exclusive upper bound: dir.ModTime < this instant
	SourceRoot      string
	StagingRoot     string
	StateFile       string
	AdminBaseURL    string
	AdminUser       string
	AdminPassword   string
	AllowFutureDate bool
}

func LoadConfigFromEnv() *Config {
	return &Config{
		SourceRoot:      firstNonEmpty(os.Getenv("IMPORT_SOURCE_ROOT"), "/mnt/DuanJu"),
		StagingRoot:     firstNonEmpty(os.Getenv("IMPORT_STAGING_ROOT"), "/www/wwwroot/short/backend/uploads/staging"),
		StateFile:       firstNonEmpty(os.Getenv("IMPORT_STATE_FILE"), "/www/wwwroot/short/backend/uploads/daoru.txt"),
		AdminBaseURL: strings.TrimSuffix(firstNonEmpty(os.Getenv("IMPORT_ADMIN_BASE_URL"), "http://127.0.0.1:8080/api/v1/admin"), "/"),
		AdminUser:    os.Getenv("IMPORT_ADMIN_USER"),
		// AdminPassword resolved in main (CLI / IMPORT_ADMIN_PASSWORD / IMPORT_ADMIN_PASSWORD_FILE).
	}
}

func firstNonEmpty(a, b string) string {
	if strings.TrimSpace(a) != "" {
		return a
	}
	return b
}

func ParseOptionalMaxDirs(s string) (*int, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, nil
	}
	n, err := strconv.Atoi(s)
	if err != nil {
		return nil, fmt.Errorf("max_dirs_per_run: %w", err)
	}
	if n < 1 || n > maxDirsUpperBound {
		return nil, fmt.Errorf("max_dirs_per_run must be between 1 and %d", maxDirsUpperBound)
	}
	return &n, nil
}

// ParseModTimeBefore parses YYYY-MM-DD as local midnight boundary (ModTime < that instant).
func ParseModTimeBefore(s string) (*time.Time, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, nil
	}
	layouts := []string{"2006-01-02", time.RFC3339, "2006-01-02 15:04:05"}
	var t time.Time
	var err error
	for _, ly := range layouts {
		t, err = time.ParseInLocation(ly, s, time.Local)
		if err == nil {
			break
		}
	}
	if err != nil {
		return nil, fmt.Errorf("mod_time_before: cannot parse %q (try YYYY-MM-DD)", s)
	}
	if len(s) == len("2006-01-02") && strings.Count(s, ":") == 0 && strings.Count(s, "T") == 0 {
		t = time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, time.Local)
	}
	minT := time.Date(1970, 1, 1, 0, 0, 0, 0, time.Local)
	if t.Before(minT) {
		return nil, fmt.Errorf("mod_time_before before 1970-01-01 not allowed")
	}
	return &t, nil
}

func ValidateModTimeUpperBound(t time.Time, allowFuture bool) error {
	if allowFuture {
		return nil
	}
	if t.After(time.Now().Add(24 * time.Hour)) {
		return fmt.Errorf("mod_time_before too far in future (> now+24h); use --allow-future-before-date to override")
	}
	return nil
}

func (c *Config) PrintBanner() {
	out := os.Stderr
	fmt.Fprintf(out, "[drama-import] source_root=%s\n", c.SourceRoot)
	fmt.Fprintf(out, "[drama-import] staging_root=%s\n", c.StagingRoot)
	fmt.Fprintf(out, "[drama-import] state_file=%s\n", c.StateFile)
	fmt.Fprintf(out, "[drama-import] admin_base_url=%s\n", c.AdminBaseURL)
	if c.MaxDirsPerRun != nil {
		fmt.Fprintf(out, "[drama-import] max_dirs_per_run=%d\n", *c.MaxDirsPerRun)
	} else {
		fmt.Fprintf(out, "[drama-import] max_dirs_per_run=<unset unlimited>\n")
	}
	if c.ModTimeBefore != nil {
		fmt.Fprintf(out, "[drama-import] mod_time_before=%s (require ModTime < this instant, local TZ)\n", c.ModTimeBefore.Format(time.RFC3339))
	} else {
		fmt.Fprintf(out, "[drama-import] mod_time_before=<unset no mtime filter>\n")
	}
	if c.MaxDirsPerRun == nil && c.ModTimeBefore == nil {
		fmt.Fprintf(out, "[drama-import] WARNING: no max_dirs and no mod_time_before — will scan ALL subdirs under source (long run possible)\n")
	}
}

// Record is one line in daoru.txt (JSON).
type Record struct {
	Dir            string `json:"dir"`
	Status         string `json:"status"`
	DramaID        uint64 `json:"drama_id,omitempty"`
	CoverURL       string `json:"cover_url,omitempty"`
	LastOkEpisode  int    `json:"last_ok_episode,omitempty"`
	SeriesIDWarned bool   `json:"series_id_warned,omitempty"`
	UpdatedAt      string `json:"updated_at"`
	LastError      string `json:"last_error,omitempty"`
}

func (r *Record) touch() {
	r.UpdatedAt = time.Now().Format(time.RFC3339)
}

// envelope matches backend utils.Response
type envelope struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}
