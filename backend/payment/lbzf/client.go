package lbzf

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// 复用 Client，避免每次请求分配新 Transport/超时器（行为与原先一致：每请求 30s 超时）。
var httpClient = &http.Client{Timeout: 30 * time.Second}

func postSignedForm(endpoint string, pairs map[string]string) ([]byte, error) {
	v := url.Values{}
	for k, val := range pairs {
		if k == "sign" {
			continue
		}
		v.Set(k, val)
	}
	v.Set("sign", pairs["sign"])
	req, err := http.NewRequest(http.MethodPost, endpoint, strings.NewReader(v.Encode()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	return b, nil
}

// CreateOrder 统一下单；extra 为附加参数，无则传 "-"。
func CreateOrder(apiBase, mchID, signKey, notifyURL, subject, body, extra, mchOrderNo, productID4 string, amountFen int) (payURL, payOrderID string, err error) {
	pairs := map[string]string{
		"mchId":      mchID,
		"productId":  productID4,
		"mchOrderNo": mchOrderNo,
		"amount":     fmt.Sprintf("%d", amountFen),
		"notifyUrl":  notifyURL,
		"subject":    subject,
		"body":       body,
		"extra":      extra,
	}
	if pairs["extra"] == "" {
		pairs["extra"] = "-"
	}
	pairs["sign"] = Sign(pairs, signKey)
	b, err := postSignedForm(strings.TrimRight(apiBase, "/")+"/create_order", pairs)
	if err != nil {
		return "", "", err
	}
	var raw map[string]json.RawMessage
	if err := json.Unmarshal(b, &raw); err != nil {
		return "", "", fmt.Errorf("parse response: %w body=%s", err, string(b))
	}
	var retCode, retMsg string
	if v, ok := raw["retCode"]; ok {
		_ = json.Unmarshal(v, &retCode)
	}
	if v, ok := raw["retMsg"]; ok {
		_ = json.Unmarshal(v, &retMsg)
	}
	if !strings.EqualFold(retCode, "SUCCESS") {
		if retMsg == "" {
			retMsg = "下单失败"
		}
		return "", "", fmt.Errorf("%s", retMsg)
	}
	// payParams 可能是 object 或 string
	if po, ok := raw["payOrderId"]; ok {
		_ = json.Unmarshal(po, &payOrderID)
	}
	var payParams any
	if pp, ok := raw["payParams"]; ok {
		if err := json.Unmarshal(pp, &payParams); err != nil {
			return "", "", fmt.Errorf("payParams: %w", err)
		}
	}
	payURL, err = extractPayURL(payParams)
	if err != nil {
		return "", payOrderID, err
	}
	return payURL, payOrderID, nil
}

func extractPayURL(payParams any) (string, error) {
	if payParams == nil {
		return "", fmt.Errorf("empty payParams")
	}
	// 已是 map
	if m, ok := payParams.(map[string]any); ok {
		if u, _ := m["payUrl"].(string); u != "" {
			return u, nil
		}
	}
	// 再解一层 JSON string
	if s, ok := payParams.(string); ok {
		var m map[string]any
		if err := json.Unmarshal([]byte(s), &m); err == nil {
			if u, _ := m["payUrl"].(string); u != "" {
				return u, nil
			}
		}
	}
	return "", fmt.Errorf("no payUrl in payParams")
}

// QueryOrder 查单；mch 与 payOrder 二传一，另一为空串。
func QueryOrder(apiBase, mchID, signKey, mchOrderNo, payOrderID string) (amount int, status int, outPayOrderID string, err error) {
	pairs := map[string]string{
		"mchId": mchID,
		"sign":  "",
	}
	if mchOrderNo != "" {
		pairs["mchOrderNo"] = mchOrderNo
	}
	if payOrderID != "" {
		pairs["payOrderId"] = payOrderID
	}
	delete(pairs, "sign")
	pairs["sign"] = Sign(pairs, signKey)
	b, err := postSignedForm(strings.TrimRight(apiBase, "/")+"/query_order", pairs)
	if err != nil {
		return 0, 0, "", err
	}
	var out struct {
		RetCode    string `json:"retCode"`
		RetMsg     string `json:"retMsg"`
		Amount     int    `json:"amount"`
		Status     int    `json:"status"`
		PayOrderID string `json:"payOrderId"`
	}
	if err := json.Unmarshal(b, &out); err != nil {
		return 0, 0, "", fmt.Errorf("query parse: %w body=%s", err, string(b))
	}
	if !strings.EqualFold(out.RetCode, "SUCCESS") {
		if out.RetMsg == "" {
			out.RetMsg = "查单失败"
		}
		return 0, 0, "", fmt.Errorf("%s", out.RetMsg)
	}
	return out.Amount, out.Status, out.PayOrderID, nil
}
