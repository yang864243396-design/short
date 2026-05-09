package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	pathpkg "path/filepath"
	"strings"
	"time"
)

// Client calls admin JSON API under cfg.AdminBaseURL (…/api/v1/admin).
type Client struct {
	baseURL  string
	user     string
	password string
	httpc    *http.Client
	token    string
}

func NewClient(baseURL, user, password string) *Client {
	return &Client{
		baseURL:  strings.TrimSuffix(strings.TrimSpace(baseURL), "/"),
		user:     user,
		password: password,
		httpc: &http.Client{
			Timeout: 120 * time.Minute,
		},
	}
}

func (c *Client) Login() error {
	body := map[string]string{"username": c.user, "password": c.password}
	var loginData struct {
		Token string `json:"token"`
	}
	if err := c.postJSONNoAuth("/login", body, &loginData); err != nil {
		return err
	}
	if loginData.Token == "" {
		return fmt.Errorf("login: empty token")
	}
	c.token = loginData.Token
	return nil
}

func (c *Client) ensureAuth() error {
	if c.token != "" {
		return nil
	}
	return c.Login()
}

func (c *Client) postJSONNoAuth(path string, reqBody interface{}, out interface{}) error {
	b, err := json.Marshal(reqBody)
	if err != nil {
		return err
	}
	url := c.baseURL + path
	httpReq, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(b))
	if err != nil {
		return err
	}
	httpReq.Header.Set("Content-Type", "application/json")
	resp, err := c.httpc.Do(httpReq)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return err
	}
	return decodeEnvelope(resp.StatusCode, raw, out)
}

func (c *Client) doJSON(method, path string, reqBody interface{}, out interface{}) error {
	return c.doJSONRetry(method, path, reqBody, out, true)
}

func (c *Client) doJSONRetry(method, path string, reqBody interface{}, out interface{}, allowRelogin bool) error {
	if err := c.ensureAuth(); err != nil {
		return err
	}
	var body io.Reader
	if reqBody != nil {
		b, err := json.Marshal(reqBody)
		if err != nil {
			return err
		}
		body = bytes.NewReader(b)
	}
	url := c.baseURL + path
	httpReq, err := http.NewRequest(method, url, body)
	if err != nil {
		return err
	}
	if reqBody != nil {
		httpReq.Header.Set("Content-Type", "application/json")
	}
	httpReq.Header.Set("Authorization", "Bearer "+c.token)
	resp, err := c.httpc.Do(httpReq)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return err
	}
	if resp.StatusCode == http.StatusUnauthorized && allowRelogin {
		c.token = ""
		if err := c.Login(); err != nil {
			return err
		}
		return c.doJSONRetry(method, path, reqBody, out, false)
	}
	return decodeEnvelope(resp.StatusCode, raw, out)
}

func decodeEnvelope(httpCode int, raw []byte, out interface{}) error {
	var env envelope
	if err := json.Unmarshal(raw, &env); err != nil {
		return fmt.Errorf("HTTP %d invalid JSON: %w body=%s", httpCode, err, truncate(string(raw), 200))
	}
	if env.Code != 200 {
		return fmt.Errorf("API code=%d msg=%s http=%d", env.Code, env.Message, httpCode)
	}
	if out == nil || len(env.Data) == 0 || string(env.Data) == "null" {
		return nil
	}
	if err := json.Unmarshal(env.Data, out); err != nil {
		return fmt.Errorf("decode data: %w", err)
	}
	return nil
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "…"
}

type categoryRow struct {
	ID   uint64 `json:"id"`
	Name string `json:"name"`
	Sort int    `json:"sort"`
}

type categoriesPage struct {
	List     []categoryRow `json:"list"`
	Total    int64         `json:"total"`
	Page     int           `json:"page"`
	PageSize int           `json:"page_size"`
}

func (c *Client) fetchAllCategories() (map[string]uint64, error) {
	out := make(map[string]uint64)
	page := 1
	pageSize := 100
	for {
		path := fmt.Sprintf("/categories?page=%d&page_size=%d", page, pageSize)
		var data categoriesPage
		if err := c.doJSON(http.MethodGet, path, nil, &data); err != nil {
			return nil, err
		}
		for _, row := range data.List {
			out[row.Name] = row.ID
		}
		if len(data.List) < pageSize || int64(page*pageSize) >= data.Total {
			break
		}
		page++
	}
	return out, nil
}

func (c *Client) createCategory(name string) (uint64, error) {
	body := map[string]interface{}{"name": name, "sort": 10}
	var row categoryRow
	if err := c.doJSON(http.MethodPost, "/categories", body, &row); err != nil {
		return 0, err
	}
	return row.ID, nil
}

func (c *Client) EnsureCategoryNames(names []string) (commaJoined string, err error) {
	m, err := c.fetchAllCategories()
	if err != nil {
		return "", err
	}
	for _, nm := range names {
		if strings.TrimSpace(nm) == "" {
			continue
		}
		if _, ok := m[nm]; ok {
			continue
		}
		if _, err := c.createCategory(nm); err != nil {
			return "", fmt.Errorf("create category %q: %w", nm, err)
		}
		m2, err := c.fetchAllCategories()
		if err != nil {
			return "", err
		}
		m = m2
	}
	var parts []string
	for _, nm := range names {
		if strings.TrimSpace(nm) == "" {
			continue
		}
		parts = append(parts, nm)
	}
	return strings.Join(parts, ","), nil
}

type dramaRow struct {
	ID uint64 `json:"id"`
}

func (c *Client) CreateDrama(title, description, category, coverURL string, totalEpisodes int, heat int64) (uint64, error) {
	body := map[string]interface{}{
		"title":          title,
		"description":    description,
		"category":       category,
		"cover_url":      coverURL,
		"total_episodes": totalEpisodes,
		"rating":         9.9,
		"heat":           heat,
		"status":         "completed",
		"enabled":        false,
	}
	var dr dramaRow
	if err := c.doJSON(http.MethodPost, "/dramas", body, &dr); err != nil {
		return 0, err
	}
	return dr.ID, nil
}

func (c *Client) UploadImage(localPath string) (publicURL string, err error) {
	return c.uploadMultipartRetry("/upload/image", localPath, true)
}

// UploadVideo returns server-relative video_path (e.g. ./uploads/videos/…).
func (c *Client) UploadVideo(localPath string) (diskPath string, err error) {
	return c.uploadMultipartRetry("/upload/video", localPath, true)
}

func (c *Client) uploadMultipartRetry(apiPath, localPath string, allowRelogin bool) (string, error) {
	if err := c.ensureAuth(); err != nil {
		return "", err
	}
	f, err := os.Open(localPath)
	if err != nil {
		return "", err
	}
	defer f.Close()
	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)
	part, err := w.CreateFormFile("file", pathpkg.Base(localPath))
	if err != nil {
		return "", err
	}
	if _, err := io.Copy(part, f); err != nil {
		return "", err
	}
	if err := w.Close(); err != nil {
		return "", err
	}
	url := c.baseURL + apiPath
	req, err := http.NewRequest(http.MethodPost, url, &buf)
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", w.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+c.token)
	resp, err := c.httpc.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	if resp.StatusCode == http.StatusUnauthorized {
		if !allowRelogin {
			return "", fmt.Errorf("upload: HTTP 401 after relogin")
		}
		c.token = ""
		if err := c.Login(); err != nil {
			return "", err
		}
		return c.uploadMultipartRetry(apiPath, localPath, false)
	}
	var env envelope
	if err := json.Unmarshal(raw, &env); err != nil {
		return "", fmt.Errorf("upload invalid JSON: %w", err)
	}
	if env.Code != 200 {
		return "", fmt.Errorf("upload: %s", env.Message)
	}
	var data map[string]interface{}
	if err := json.Unmarshal(env.Data, &data); err != nil {
		return "", err
	}
	if apiPath == "/upload/image" {
		u, _ := data["url"].(string)
		return u, nil
	}
	p, _ := data["path"].(string)
	return p, nil
}

type episodeRow struct {
	ID uint64 `json:"id"`
}

func (c *Client) CreateEpisode(dramaID uint64, epNum int, title, videoPath string, isFree bool, unlockCoins int) error {
	body := map[string]interface{}{
		"drama_id":        dramaID,
		"episode_number":  epNum,
		"title":           title,
		"video_path":      videoPath,
		"is_free":         isFree,
		"unlock_coins":    unlockCoins,
	}
	var ep episodeRow
	if err := c.doJSON(http.MethodPost, "/episodes", body, &ep); err != nil {
		return err
	}
	return nil
}

func isDuplicateEpisodeErr(err error) bool {
	if err == nil {
		return false
	}
	s := err.Error()
	return strings.Contains(s, "已存在")
}
