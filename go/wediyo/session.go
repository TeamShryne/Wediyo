package wediyo

import (
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

const ytURL = "https://www.youtube.com/"
const userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

func extractJSONString(html, key string) (string, bool) {
	needle := `"` + key + `":"`
	idx := strings.Index(html, needle)
	if idx == -1 {
		return "", false
	}
	start := idx + len(needle)
	rest := html[start:]
	end := strings.Index(rest, `"`)
	if end == -1 {
		return "", false
	}
	return rest[:end], true
}

func parseSetCookies(header http.Header) map[string]string {
	m := make(map[string]string)
	for _, v := range header["Set-Cookie"] {
		// first segment before ';' is NAME=VALUE
		parts := strings.SplitN(v, ";", 2)
		pair := strings.TrimSpace(parts[0])
		if eq := strings.Index(pair, "="); eq != -1 {
			name := strings.TrimSpace(pair[:eq])
			val := strings.TrimSpace(pair[eq+1:])
			if name != "" {
				m[name] = val
			}
		}
	}
	return m
}

// FetchInnertubeSession generates YSC/VISITOR_INFO1_LIVE/YNID/ROLLOUT_TOKEN/VISITOR_DATA from server
// Call once at startup and reuse same session for pagination.
func FetchInnertubeSession() (*InnertubeSession, error) {
	client := &http.Client{Timeout: 15 * time.Second}
	req, err := http.NewRequest("GET", ytURL, nil)
	if err != nil {
		return nil, fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("User-Agent", userAgent)
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("GET youtube.com: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("youtube.com status %d", resp.StatusCode)
	}
	cookies := parseSetCookies(resp.Header)
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read html: %w", err)
	}
	html := string(body)

	visitorData, ok := extractJSONString(html, "VISITOR_DATA")
	if !ok || visitorData == "" {
		return nil, fmt.Errorf("VISITOR_DATA not found")
	}
	apiKey, ok := extractJSONString(html, "INNERTUBE_API_KEY")
	if !ok || apiKey == "" {
		return nil, fmt.Errorf("INNERTUBE_API_KEY not found")
	}
	clientVersion, ok := extractJSONString(html, "INNERTUBE_CLIENT_VERSION")
	if !ok || clientVersion == "" {
		clientVersion = "2.20260826.01.00"
	}
	clientName, ok := extractJSONString(html, "INNERTUBE_CLIENT_NAME")
	if !ok || clientName == "" {
		clientName = "WEB"
	}

	ysc := cookies["YSC"]
	visitorInfoLive := cookies["VISITOR_INFO1_LIVE"]
	secureYNID := cookies["__Secure-YNID"]
	rolloutToken := cookies["__Secure-ROLLOUT_TOKEN"]
	visitorPrivacyMetadata := cookies["VISITOR_PRIVACY_METADATA"]
	pref := cookies["PREF"]
	if pref == "" {
		pref = "f4=4000000&tz=UTC"
	}
	gps := cookies["GPS"]
	if gps == "" {
		gps = "1"
	}

	var parts []string
	if ysc != "" {
		parts = append(parts, "YSC="+ysc)
	}
	if visitorInfoLive != "" {
		parts = append(parts, "VISITOR_INFO1_LIVE="+visitorInfoLive)
	}
	if secureYNID != "" {
		parts = append(parts, "__Secure-YNID="+secureYNID)
	}
	if rolloutToken != "" {
		parts = append(parts, "__Secure-ROLLOUT_TOKEN="+rolloutToken)
	}
	if visitorPrivacyMetadata != "" {
		parts = append(parts, "VISITOR_PRIVACY_METADATA="+visitorPrivacyMetadata)
	}
	parts = append(parts, "GPS="+gps)
	parts = append(parts, "PREF="+pref)
	cookieHeader := strings.Join(parts, "; ")

	return &InnertubeSession{
		VisitorData:            visitorData,
		APIKey:                 apiKey,
		ClientVersion:          clientVersion,
		ClientName:             clientName,
		YSC:                    ysc,
		VisitorInfoLive:        visitorInfoLive,
		SecureYNID:             secureYNID,
		RolloutToken:           rolloutToken,
		VisitorPrivacyMetadata: visitorPrivacyMetadata,
		Pref:                   pref,
		GPS:                    gps,
		CookieHeader:           cookieHeader,
	}, nil
}
