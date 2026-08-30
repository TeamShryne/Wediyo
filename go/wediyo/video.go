package wediyo

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"
)

// helpers reused from search.go/channel.go

func getNested(m map[string]interface{}, keys ...string) interface{} {
	var cur interface{} = m
	for _, k := range keys {
		if mm, ok := cur.(map[string]interface{}); ok {
			cur = mm[k]
		} else {
			return nil
		}
	}
	return cur
}

func parseViewCountRaw(s string) int64 {
	var digits strings.Builder
	for _, c := range s {
		if c >= '0' && c <= '9' {
			digits.WriteRune(c)
		}
	}
	if digits.Len() == 0 {
		return 0
	}
	var n int64
	fmt.Sscan(digits.String(), &n)
	return n
}

func formatDuration(secs int64) string {
	if secs <= 0 {
		return ""
	}
	h := secs / 3600
	m := (secs % 3600) / 60
	s := secs % 60
	if h > 0 {
		return fmt.Sprintf("%d:%02d:%02d", h, m, s)
	}
	return fmt.Sprintf("%d:%02d", m, s)
}

func parseDurationSeconds(s string) int64 {
	if s == "" {
		return 0
	}
	var n int64
	fmt.Sscan(strings.TrimSpace(s), &n)
	return n
}

// postInnertube is shared POST helper for player/next
func postInnertube(session *InnertubeSession, endpoint string, body map[string]interface{}) (map[string]interface{}, int, error) {
	client := &http.Client{Timeout: 20 * time.Second}
	urlStr := fmt.Sprintf("https://www.youtube.com/youtubei/v1/%s?prettyPrint=false&key=%s", endpoint, session.APIKey)
	tz := "UTC"
	if idx := strings.Index(session.Pref, "tz="); idx != -1 {
		rest := session.Pref[idx+3:]
		if amp := strings.Index(rest, "&"); amp != -1 {
			tz = rest[:amp]
		} else {
			tz = rest
		}
	}
	// Ensure body has context.client if not already
	if _, ok := body["context"]; !ok {
		context := map[string]interface{}{
			"client": map[string]interface{}{
				"hl": "en", "gl": "IN", "remoteHost": "", "deviceMake": "", "deviceModel": "",
				"visitorData": session.VisitorData, "userAgent": userAgent + ",gzip(gfe)", "clientName": session.ClientName, "clientVersion": session.ClientVersion,
				"osName": "Windows", "osVersion": "10.0", "originalUrl": "https://www.youtube.com/watch?v=" + fmt.Sprintf("%v", body["videoId"]), "screenPixelDensity": 2, "platform": "DESKTOP", "clientFormFactor": "UNKNOWN_FORM_FACTOR",
				"configInfo": map[string]interface{}{}, "timeZone": tz, "browserName": "Chrome", "browserVersion": "124.0.0.0",
				"acceptHeader": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "screenWidthPoints": 1280, "screenHeightPoints": 720, "utcOffsetMinutes": 0, "userInterfaceTheme": "USER_INTERFACE_THEME_LIGHT",
			},
			"user":    map[string]interface{}{"lockedSafetyMode": false},
			"request": map[string]interface{}{"useSsl": true, "internalExperimentFlags": []interface{}{}, "consistencyTokenJars": []interface{}{}},
		}
		if session.RolloutToken != "" {
			if c, ok := context["client"].(map[string]interface{}); ok {
				c["rolloutToken"] = session.RolloutToken
			}
		}
		body["context"] = context
	}
	// fix originalUrl if videoId present
	if vid, ok := body["videoId"].(string); ok && vid != "" {
		if ctx, ok := body["context"].(map[string]interface{}); ok {
			if cli, ok := ctx["client"].(map[string]interface{}); ok {
				cli["originalUrl"] = "https://www.youtube.com/watch?v=" + vid
			}
		}
	}
	bodyBytes, _ := json.Marshal(body)
	req, err := http.NewRequest("POST", urlStr, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Origin", "https://www.youtube.com")
	if vid, ok := body["videoId"].(string); ok && vid != "" {
		req.Header.Set("Referer", "https://www.youtube.com/watch?v="+vid)
	} else {
		req.Header.Set("Referer", "https://www.youtube.com/")
	}
	req.Header.Set("X-Goog-Visitor-Id", session.VisitorData)
	req.Header.Set("X-Youtube-Client-Name", "1")
	req.Header.Set("X-Youtube-Client-Version", session.ClientVersion)
	req.Header.Set("X-Youtube-Bootstrap-Logged-In", "false")
	req.Header.Set("Cookie", session.CookieHeader)
	req.Header.Set("User-Agent", userAgent)
	resp, err := client.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("%s POST: %w", endpoint, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		buf := new(bytes.Buffer)
		buf.ReadFrom(resp.Body)
		s := buf.String()
		if len(s) > 800 {
			s = s[:800]
		}
		return nil, resp.StatusCode, fmt.Errorf("%s status %d: %s", endpoint, resp.StatusCode, s)
	}
	var j map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&j); err != nil {
		return nil, resp.StatusCode, fmt.Errorf("parse %s json: %w", endpoint, err)
	}
	return j, resp.StatusCode, nil
}

const visionosUA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
const visionosVersion = "1.02"
const visionosClientName = "VISIONOS"
const visionosClientVersionExtra = "" // keep simple

func postInnertubeWithClient(session *InnertubeSession, endpoint string, body map[string]interface{}, clientName, clientVersion, ua string) (map[string]interface{}, int, error) {
	client := &http.Client{Timeout: 20 * time.Second}
	urlStr := fmt.Sprintf("https://www.youtube.com/youtubei/v1/%s?prettyPrint=false&key=%s", endpoint, session.APIKey)
	// ensure body has context.client with provided clientName/version/ua
	if _, ok := body["context"]; !ok {
		// fallback to normal post
		return postInnertube(session, endpoint, body)
	}
	bodyBytes, _ := json.Marshal(body)
	req, err := http.NewRequest("POST", urlStr, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Origin", "https://www.youtube.com")
	if vid, ok := body["videoId"].(string); ok && vid != "" {
		req.Header.Set("Referer", "https://www.youtube.com/watch?v="+vid)
	} else {
		req.Header.Set("Referer", "https://www.youtube.com/")
	}
	req.Header.Set("X-Goog-Visitor-Id", session.VisitorData)
	// Map clientName to Innertube clientId for header: VISIONOS -> 101, WEB -> 1, MWEB -> 2, etc.
	clientId := "1"
	switch clientName {
	case "VISIONOS":
		clientId = "101"
	case "MWEB":
		clientId = "2"
	case "ANDROID":
		clientId = "3"
	default:
		clientId = "1"
	}
	req.Header.Set("X-Youtube-Client-Name", clientId)
	req.Header.Set("X-Youtube-Client-Version", clientVersion)
	req.Header.Set("X-Youtube-Bootstrap-Logged-In", "false")
	req.Header.Set("Cookie", session.CookieHeader)
	req.Header.Set("User-Agent", ua)
	resp, err := client.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("%s POST: %w", endpoint, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		buf := new(bytes.Buffer)
		buf.ReadFrom(resp.Body)
		s := buf.String()
		if len(s) > 800 {
			s = s[:800]
		}
		return nil, resp.StatusCode, fmt.Errorf("%s status %d: %s", endpoint, resp.StatusCode, s)
	}
	var j map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&j); err != nil {
		return nil, resp.StatusCode, fmt.Errorf("parse %s json: %w", endpoint, err)
	}
	return j, resp.StatusCode, nil
}

func fetchPlayer(session *InnertubeSession, videoId string) (map[string]interface{}, error) {
	body := map[string]interface{}{
		"videoId":        videoId,
		"contentCheckOk": true,
		"racyCheckOk":    true,
	}
	// Build context explicitly with WEB client like search
	tz := "UTC"
	if idx := strings.Index(session.Pref, "tz="); idx != -1 {
		rest := session.Pref[idx+3:]
		if amp := strings.Index(rest, "&"); amp != -1 {
			tz = rest[:amp]
		} else {
			tz = rest
		}
	}
	context := map[string]interface{}{
		"client": map[string]interface{}{
			"hl": "en", "gl": "IN", "remoteHost": "", "deviceMake": "", "deviceModel": "",
			"visitorData": session.VisitorData, "userAgent": userAgent + ",gzip(gfe)", "clientName": session.ClientName, "clientVersion": session.ClientVersion,
			"osName": "Windows", "osVersion": "10.0", "originalUrl": "https://www.youtube.com/watch?v=" + videoId, "screenPixelDensity": 2, "platform": "DESKTOP", "clientFormFactor": "UNKNOWN_FORM_FACTOR",
			"configInfo": map[string]interface{}{}, "timeZone": tz, "browserName": "Chrome", "browserVersion": "124.0.0.0",
			"acceptHeader": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "screenWidthPoints": 1280, "screenHeightPoints": 720, "utcOffsetMinutes": 0, "userInterfaceTheme": "USER_INTERFACE_THEME_LIGHT",
		},
		"user":    map[string]interface{}{"lockedSafetyMode": false},
		"request": map[string]interface{}{"useSsl": true, "internalExperimentFlags": []interface{}{}, "consistencyTokenJars": []interface{}{}},
	}
	if session.RolloutToken != "" {
		if c, ok := context["client"].(map[string]interface{}); ok {
			c["rolloutToken"] = session.RolloutToken
		}
	}
	body["context"] = context
	j, _, err := postInnertube(session, "player", body)
	return j, err
}

func fetchPlayerVisionOS(session *InnertubeSession, videoId string) (map[string]interface{}, error) {
	body := map[string]interface{}{
		"videoId":        videoId,
		"contentCheckOk": true,
		"racyCheckOk":    true,
	}
	tz := "UTC"
	if idx := strings.Index(session.Pref, "tz="); idx != -1 {
		rest := session.Pref[idx+3:]
		if amp := strings.Index(rest, "&"); amp != -1 {
			tz = rest[:amp]
		} else {
			tz = rest
		}
	}
	context := map[string]interface{}{
		"client": map[string]interface{}{
			"hl": "en", "gl": "US", "visitorData": session.VisitorData, "userAgent": visionosUA + ",gzip(gfe)",
			"clientName": visionosClientName, "clientVersion": visionosVersion,
			"osName": "visionOS", "osVersion": "26.5.23O471", "deviceMake": "Apple", "deviceModel": "RealityDevice17,1",
			"originalUrl": "https://www.youtube.com/watch?v=" + videoId, "platform": "DESKTOP",
			"configInfo": map[string]interface{}{}, "timeZone": tz, "browserName": "", "browserVersion": "",
			"screenPixelDensity": 2, "clientFormFactor": "UNKNOWN_FORM_FACTOR",
		},
		"user":    map[string]interface{}{"lockedSafetyMode": false},
		"request": map[string]interface{}{"useSsl": true},
	}
	body["context"] = context
	j, _, err := postInnertubeWithClient(session, "player", body, visionosClientName, visionosVersion, visionosUA)
	return j, err
}

func fetchNext(session *InnertubeSession, videoId string) (map[string]interface{}, error) {
	body := map[string]interface{}{
		"videoId": videoId,
	}
	tz := "UTC"
	if idx := strings.Index(session.Pref, "tz="); idx != -1 {
		rest := session.Pref[idx+3:]
		if amp := strings.Index(rest, "&"); amp != -1 {
			tz = rest[:amp]
		} else {
			tz = rest
		}
	}
	context := map[string]interface{}{
		"client": map[string]interface{}{
			"hl": "en", "gl": "IN", "remoteHost": "", "deviceMake": "", "deviceModel": "",
			"visitorData": session.VisitorData, "userAgent": userAgent + ",gzip(gfe)", "clientName": session.ClientName, "clientVersion": session.ClientVersion,
			"osName": "Windows", "osVersion": "10.0", "originalUrl": "https://www.youtube.com/watch?v=" + videoId, "screenPixelDensity": 2, "platform": "DESKTOP", "clientFormFactor": "UNKNOWN_FORM_FACTOR",
			"configInfo": map[string]interface{}{}, "timeZone": tz, "browserName": "Chrome", "browserVersion": "124.0.0.0",
			"acceptHeader": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "screenWidthPoints": 1280, "screenHeightPoints": 720, "utcOffsetMinutes": 0, "userInterfaceTheme": "USER_INTERFACE_THEME_LIGHT",
		},
		"user":    map[string]interface{}{"lockedSafetyMode": false},
		"request": map[string]interface{}{"useSsl": true, "internalExperimentFlags": []interface{}{}, "consistencyTokenJars": []interface{}{}},
	}
	if session.RolloutToken != "" {
		if c, ok := context["client"].(map[string]interface{}); ok {
			c["rolloutToken"] = session.RolloutToken
		}
	}
	body["context"] = context
	j, _, err := postInnertube(session, "next", body)
	return j, err
}

// parse streaming formats from player streamingData
func parseStreamingFormats(raw interface{}) (formats []StreamingFormat, adaptive []StreamingFormat) {
	m := asMap(raw)
	if m == nil {
		return nil, nil
	}
	sd, ok := m["streamingData"].(map[string]interface{})
	if !ok {
		return nil, nil
	}
	parseList := func(arr []interface{}) []StreamingFormat {
		var out []StreamingFormat
		for _, e := range arr {
			if em, ok := e.(map[string]interface{}); ok {
				f := StreamingFormat{}
				if v, ok := em["itag"].(float64); ok {
					f.Itag = int(v)
				}
				f.URL, _ = em["url"].(string)
				f.MimeType, _ = em["mimeType"].(string)
				if v, ok := em["bitrate"].(float64); ok {
					f.Bitrate = int(v)
				}
				if v, ok := em["width"].(float64); ok {
					f.Width = int(v)
				}
				if v, ok := em["height"].(float64); ok {
					f.Height = int(v)
				}
				f.Quality, _ = em["quality"].(string)
				if v, ok := em["fps"].(float64); ok {
					f.FPS = int(v)
				}
				f.QualityLabel, _ = em["qualityLabel"].(string)
				f.ApproxDurationMs, _ = em["approxDurationMs"].(string)
				f.AudioQuality, _ = em["audioQuality"].(string)
				if v, ok := em["audioSampleRate"].(float64); ok {
					f.AudioSampleRate = int(v)
				}
				if v, ok := em["audioChannels"].(float64); ok {
					f.AudioChannels = int(v)
				}
				if v, ok := em["contentLength"].(string); ok {
					f.ContentLength = v
				} else if v, ok := em["contentLength"].(float64); ok {
					f.ContentLength = strconv.FormatInt(int64(v), 10)
				}
				if v, ok := em["averageBitrate"].(float64); ok {
					f.AverageBitrate = int(v)
				}
				if v, ok := em["lastModified"].(float64); ok {
					f.LastModified = strconv.FormatInt(int64(v), 10)
				} else if v, ok := em["lastModified"].(string); ok {
					f.LastModified = v
				}
				f.SignatureCipher, _ = em["signatureCipher"].(string)
				f.Cipher, _ = em["cipher"].(string)
				if v, ok := em["loudnessDb"].(float64); ok {
					f.LoudnessDb = v
				}
				if at, ok := em["audioTrack"].(map[string]interface{}); ok {
					vt := &VideoAudioTrack{}
					vt.DisplayName, _ = at["displayName"].(string)
					vt.ID, _ = at["id"].(string)
					if b, ok := at["audioIsDefault"].(bool); ok {
						vt.IsDefault = b
					}
					if b, ok := at["isAutoDubbed"].(bool); ok {
						vt.IsAutoDubbed = b
					}
					f.AudioTrack = vt
				}
				if b, ok := em["isDrc"].(bool); ok {
					f.IsDRC = b
				}
				f.XTags, _ = em["xtags"].(string)
				if r, ok := em["initRange"].(map[string]interface{}); ok {
					f.InitRangeStart, _ = r["start"].(string)
					f.InitRangeEnd, _ = r["end"].(string)
				}
				if r, ok := em["indexRange"].(map[string]interface{}); ok {
					f.IndexRangeStart, _ = r["start"].(string)
					f.IndexRangeEnd, _ = r["end"].(string)
				}
				f.IsAudio = f.Width == 0 && f.Height == 0
				// DRC detection via xtags drc=1
				if f.XTags != "" && strings.Contains(f.XTags, "drc=1") {
					f.IsDRC = true
				}
				out = append(out, f)
			}
		}
		return out
	}
	if arr, ok := sd["formats"].([]interface{}); ok {
		formats = parseList(arr)
	}
	if arr, ok := sd["adaptiveFormats"].([]interface{}); ok {
		adaptive = parseList(arr)
	}
	return formats, adaptive
}

func parsePlayerDetail(j map[string]interface{}, res *VideoDetailResult) {
	// videoDetails
	if vd, ok := j["videoDetails"].(map[string]interface{}); ok {
		res.VideoID, _ = vd["videoId"].(string)
		if t, ok := vd["title"].(string); ok {
			if res.Title == "" {
				res.Title = t
			}
		}
		res.Author, _ = vd["author"].(string)
		res.ChannelID, _ = vd["channelId"].(string)
		if s, ok := vd["lengthSeconds"].(string); ok {
			res.LengthSeconds = parseDurationSeconds(s)
			res.DurationText = formatDuration(res.LengthSeconds)
		}
		if vc, ok := vd["viewCount"].(string); ok {
			res.ViewCountRaw = vc
			res.ViewCount = parseViewCountRaw(vc)
			res.ViewCountText = vc + " views"
		}
		if kw, ok := vd["keywords"].([]interface{}); ok {
			for _, k := range kw {
				if s, ok := k.(string); ok {
					res.Keywords = append(res.Keywords, s)
				}
			}
		}
		if s, ok := vd["shortDescription"].(string); ok {
			res.ShortDescription = s
			if res.Description == "" {
				res.Description = s
			}
		}
		if b, ok := vd["isPrivate"].(bool); ok {
			res.IsPrivate = b
		}
		if b, ok := vd["isLiveContent"].(bool); ok {
			res.IsLiveContent = b
			res.IsLive = b
		}
		if b, ok := vd["isCrawlable"].(bool); ok {
			res.IsCrawlable = b
		}
		if th, ok := vd["thumbnail"]; ok {
			ths := parseThumbnails(th)
			if len(ths) > 0 {
				res.Thumbnails = ths
				res.ThumbnailURL = ths[len(ths)-1].URL
			}
		}
		if b, ok := vd["allowRatings"].(bool); ok {
			res.AllowRatings = b
		}
	}
	// microformat
	if mf, ok := j["microformat"].(map[string]interface{}); ok {
		if pmr, ok := mf["playerMicroformatRenderer"].(map[string]interface{}); ok {
			if th, ok := pmr["thumbnail"]; ok {
				ths := parseThumbnails(th)
				if len(ths) > 0 && res.ThumbnailURL == "" {
					res.Thumbnails = ths
					res.ThumbnailURL = ths[len(ths)-1].URL
				}
			}
			if emb, ok := pmr["embed"].(map[string]interface{}); ok {
				res.EmbedURL, _ = emb["iframeUrl"].(string)
			}
			if t, ok := pmr["title"].(map[string]interface{}); ok {
				if s := getText(t); s != "" {
					res.Title = s
				}
			}
			if d, ok := pmr["description"].(map[string]interface{}); ok {
				if s := getText(d); s != "" {
					res.Description = s
					if res.ShortDescription == "" {
						res.ShortDescription = s
					}
				}
			}
			if s, ok := pmr["lengthSeconds"].(string); ok && res.LengthSeconds == 0 {
				res.LengthSeconds = parseDurationSeconds(s)
				res.DurationText = formatDuration(res.LengthSeconds)
			}
			rawHandle, _ := pmr["ownerProfileUrl"].(string)
			// ownerProfileUrl is like "http://www.youtube.com/@ThePrimeTimeagen" -> extract "@ThePrimeTimeagen"
			if idx := strings.LastIndex(rawHandle, "/@"); idx != -1 {
				rawHandle = "@" + rawHandle[idx+2:]
			} else if idx := strings.LastIndex(rawHandle, "/c/"); idx != -1 {
				rawHandle = rawHandle[idx+3:]
			} else if idx := strings.LastIndex(rawHandle, "/"); idx != -1 && idx+1 < len(rawHandle) {
				// fallback: last segment
				last := rawHandle[idx+1:]
				if last != "" && !strings.Contains(last, ".") {
					rawHandle = last
				}
			}
			res.ChannelHandle = rawHandle
			res.ChannelID, _ = pmr["externalChannelId"].(string)
			if b, ok := pmr["isFamilySafe"].(bool); ok {
				res.IsFamilySafe = b
			}
			if arr, ok := pmr["availableCountries"].([]interface{}); ok {
				for _, c := range arr {
					if s, ok := c.(string); ok {
						res.AvailableCountries = append(res.AvailableCountries, s)
					}
				}
			}
			if b, ok := pmr["isUnlisted"].(bool); ok {
				res.IsUnlisted = b
			}
			if b, ok := pmr["hasYpcMetadata"].(bool); ok {
				res.HasYpcMetadata = b
			}
			if s, ok := pmr["viewCount"].(string); ok {
				// viewCount may be numeric string without commas
				if n, err := strconv.ParseInt(s, 10, 64); err == nil {
					res.ViewCount = n
					res.ViewCountText = s + " views"
					res.ViewCountRaw = s
				}
			} else if v, ok := pmr["viewCount"].(float64); ok {
				res.ViewCount = int64(v)
			}
			res.Category, _ = pmr["category"].(string)
			res.PublishDate, _ = pmr["publishDate"].(string)
			res.UploadDate, _ = pmr["uploadDate"].(string)
			if s, ok := pmr["ownerChannelName"].(string); ok {
				res.ChannelTitle = s
				if res.Author == "" {
					res.Author = s
				}
			}
			// externalVideoId
			if s, ok := pmr["externalVideoId"].(string); ok && res.VideoID == "" {
				res.VideoID = s
			}
			if s, ok := pmr["canonicalUrl"].(string); ok {
				res.CanonicalURL = s
			}
			// likeCount if present (some player responses include it)
			if s, ok := pmr["likeCount"].(string); ok {
				if n, err := strconv.ParseInt(s, 10, 64); err == nil {
					res.LikeCount = n
					res.LikeCountText = s
				}
			} else if v, ok := pmr["likeCount"].(float64); ok {
				res.LikeCount = int64(v)
			}
		}
	}
	// streamingData
	if sd, ok := j["streamingData"].(map[string]interface{}); ok {
		if v, ok := sd["expiresInSeconds"].(float64); ok {
			res.ExpiresInSeconds = int(v)
		} else if s, ok := sd["expiresInSeconds"].(string); ok {
			if n, err := strconv.Atoi(s); err == nil {
				res.ExpiresInSeconds = n
			}
		}
		res.ServerAbrStreamingURL, _ = sd["serverAbrStreamingUrl"].(string)
		res.HlsManifestURL, _ = sd["hlsManifestUrl"].(string)
		res.DashManifestURL, _ = sd["dashManifestUrl"].(string)
	}
	fmts, adapt := parseStreamingFormats(j)
	if len(fmts) > 0 {
		res.Formats = fmts
	}
	if len(adapt) > 0 {
		res.AdaptiveFormats = adapt
	}
	// captions
	if cap, ok := j["captions"].(map[string]interface{}); ok {
		if pct, ok := cap["playerCaptionsTracklistRenderer"].(map[string]interface{}); ok {
			if arr, ok := pct["captionTracks"].([]interface{}); ok {
				for _, e := range arr {
					if em, ok := e.(map[string]interface{}); ok {
						ct := VideoCaptionTrack{}
						ct.BaseURL, _ = em["baseUrl"].(string)
						if n, ok := em["name"].(map[string]interface{}); ok {
							ct.Name = getText(n)
						}
						ct.LanguageCode, _ = em["languageCode"].(string)
						ct.Kind, _ = em["kind"].(string)
						if b, ok := em["isTranslatable"].(bool); ok {
							ct.IsTranslatable = b
						}
						ct.VssID, _ = em["vssId"].(string)
						ct.TrackName, _ = em["trackName"].(string)
						res.CaptionTracks = append(res.CaptionTracks, ct)
					}
				}
			}
			if arr, ok := pct["translationLanguages"].([]interface{}); ok {
				for _, e := range arr {
					if em, ok := e.(map[string]interface{}); ok {
						tl := VideoTranslationLanguage{}
						tl.LanguageCode, _ = em["languageCode"].(string)
						if n, ok := em["languageName"].(map[string]interface{}); ok {
							tl.LanguageName = getText(n)
						}
						res.TranslationLanguages = append(res.TranslationLanguages, tl)
					}
				}
			}
			if arr, ok := pct["audioTracks"].([]interface{}); ok {
				// audioTracks are per language in captions, not streaming audioTracks — we ignore for streaming, but capture count
				_ = arr
			}
		}
	}
	// storyboards
	if sb, ok := j["storyboards"].(map[string]interface{}); ok {
		if psr, ok := sb["playerStoryboardSpecRenderer"].(map[string]interface{}); ok {
			res.StoryboardSpec, _ = psr["spec"].(string)
		}
	}
	// playabilityStatus
	if ps, ok := j["playabilityStatus"].(map[string]interface{}); ok {
		res.PlayabilityStatus, _ = ps["status"].(string)
		if b, ok := ps["playableInEmbed"].(bool); ok {
			res.PlayableInEmbed = b
		}
		// liveStreamability scheduled start etc not needed
	}
	// playerConfig audio loudness
	if pc, ok := j["playerConfig"].(map[string]interface{}); ok {
		if ac, ok := pc["audioConfig"].(map[string]interface{}); ok {
			if v, ok := ac["loudnessDb"].(float64); ok {
				res.AudioLoudnessDb = v
			}
		}
	}
	// paidContentOverlay
	if pco, ok := j["paidContentOverlay"].(map[string]interface{}); ok {
		if r, ok := pco["paidContentOverlayRenderer"].(map[string]interface{}); ok {
			res.PaidPromotionText = getText(r["text"])
		}
	}
	// trackingParams canonicalUrl already
}

// parse next response: title, view, date, channel, description, related
func parseNextDetail(j map[string]interface{}, res *VideoDetailResult) {
	// contents.twoColumnWatchNextResults.results.results.contents[]
	var primaryTitle, viewCountText, dateText, channelTitle, channelId, channelAvatar, subText, description string
	var related []VideoMetadata

	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return
	}
	two, ok := contents["twoColumnWatchNextResults"].(map[string]interface{})
	if !ok {
		return
	}
	// results.results.contents
	resultsWrap, ok := two["results"].(map[string]interface{})
	if !ok {
		return
	}
	results, ok := resultsWrap["results"].(map[string]interface{})
	if !ok {
		return
	}
	contentsArr, ok := results["contents"].([]interface{})
	if !ok {
		return
	}
	for _, c := range contentsArr {
		if cm, ok := c.(map[string]interface{}); ok {
			if vpir, ok := cm["videoPrimaryInfoRenderer"].(map[string]interface{}); ok {
				if t, ok := vpir["title"]; ok {
					if s := getText(t); s != "" {
						primaryTitle = s
					}
				}
				if vc, ok := vpir["viewCount"].(map[string]interface{}); ok {
					if vvcr, ok := vc["videoViewCountRenderer"].(map[string]interface{}); ok {
						if vc2, ok := vvcr["viewCount"]; ok {
							viewCountText = getText(vc2)
							if viewCountText != "" && res.ViewCount == 0 {
								res.ViewCount = parseViewCountRaw(viewCountText)
								res.ViewCountText = viewCountText
							}
						}
					}
				} else if vc2, ok := vpir["viewCountText"]; ok {
					viewCountText = getText(vc2)
				}
				if dt, ok := vpir["dateText"]; ok {
					dateText = getText(dt)
					if dateText != "" && res.PublishDate == "" {
						res.PublishDate = dateText
					}
					if dateText != "" && res.UploadDate == "" {
						res.UploadDate = dateText
					}
				}
				// like count sometimes in videoPrimaryInfoRenderer as topRow?
				// ignore
			}
			if vsir, ok := cm["videoSecondaryInfoRenderer"].(map[string]interface{}); ok {
				if owner, ok := vsir["owner"].(map[string]interface{}); ok {
					if vor, ok := owner["videoOwnerRenderer"].(map[string]interface{}); ok {
						if t, ok := vor["title"]; ok {
							channelTitle = getText(t)
						}
						if ne, ok := vor["navigationEndpoint"].(map[string]interface{}); ok {
							if be, ok := ne["browseEndpoint"].(map[string]interface{}); ok {
								channelId, _ = be["browseId"].(string)
							}
						}
						if th, ok := vor["thumbnail"]; ok {
							ths := parseThumbnails(th)
							if len(ths) > 0 {
								channelAvatar = ths[len(ths)-1].URL
								res.ChannelAvatars = ths
								res.ChannelAvatarURL = channelAvatar
							}
						}
						if sct, ok := vor["subscriberCountText"]; ok {
							subText = getText(sct)
						}
					}
				}
				if ad, ok := vsir["attributedDescription"].(map[string]interface{}); ok {
					if content, ok := ad["content"].(string); ok && content != "" {
						description = content
					} else if s := getText(ad["content"]); s != "" {
						description = s
					}
				} else if d, ok := vsir["description"]; ok {
					description = getText(d)
				}
			}
		}
	}
	if primaryTitle != "" && res.Title == "" {
		res.Title = primaryTitle
	}
	if viewCountText != "" {
		res.ViewCountText = viewCountText
		if res.ViewCount == 0 {
			res.ViewCount = parseViewCountRaw(viewCountText)
		}
	}
	if channelTitle != "" {
		res.ChannelTitle = channelTitle
		if res.Author == "" {
			res.Author = channelTitle
		}
	}
	if channelId != "" {
		res.ChannelID = channelId
	}
	if subText != "" {
		res.SubscriberCountText = subText
	}
	if description != "" {
		res.Description = description
		if res.ShortDescription == "" {
			res.ShortDescription = description
		}
	}
	if dateText != "" {
		res.PublishDate = dateText
		res.UploadDate = dateText
	}

	// secondaryResults.related
	secondaryWrap, ok := two["secondaryResults"].(map[string]interface{})
	if !ok {
		return
	}
	secondaryResults, ok := secondaryWrap["secondaryResults"].(map[string]interface{})
	if !ok {
		return
	}
	results2, ok := secondaryResults["results"].([]interface{})
	if !ok {
		return
	}
	for _, r := range results2 {
		if rm, ok := r.(map[string]interface{}); ok {
			if cvr, ok := rm["compactVideoRenderer"]; ok {
				if vm := parseCompactVideoRenderer(cvr); vm != nil {
					related = append(related, *vm)
				}
			} else if car, ok := rm["compactAutoplayRenderer"].(map[string]interface{}); ok {
				if contents, ok := car["contents"].([]interface{}); ok {
					for _, sub := range contents {
						if sm, ok := sub.(map[string]interface{}); ok {
							if cvr, ok := sm["compactVideoRenderer"]; ok {
								if vm := parseCompactVideoRenderer(cvr); vm != nil {
									related = append(related, *vm)
								}
							}
						}
					}
				}
			} else if isr, ok := rm["itemSectionRenderer"].(map[string]interface{}); ok {
				if contents, ok := isr["contents"].([]interface{}); ok {
					for _, sub := range contents {
						if sm, ok := sub.(map[string]interface{}); ok {
							if cvr, ok := sm["compactVideoRenderer"]; ok {
								if vm := parseCompactVideoRenderer(cvr); vm != nil {
									related = append(related, *vm)
								}
							} else if lvm, ok := sm["lockupViewModel"]; ok {
								if vm := parseLockupToVideo(lvm); vm != nil {
									related = append(related, *vm)
								}
							}
						}
					}
				}
			} else if lvm, ok := rm["lockupViewModel"]; ok {
				if vm := parseLockupToVideo(lvm); vm != nil {
					related = append(related, *vm)
				}
			}
		}
	}
	if len(related) > 0 {
		res.RelatedVideos = related
	}
	// prefer specific related continuation over generic
	if rc := extractRelatedContinuationFromNext(j); rc != "" {
		res.RelatedContinuation = rc
	} else {
		res.RelatedContinuation = extractContinuationToken(j)
	}
	// comments continuation (Mediyo flow: engagement-panel-comments-section)
	if cc := extractCommentsTokenFromNext(j); cc != "" {
		res.CommentsContinuation = cc
	}
	// comments count from engagementPanels header if available (fallback)
	if res.CommentsCountText == "" {
		if eps, ok := j["engagementPanels"].([]interface{}); ok {
			for _, ep := range eps {
				if em, ok := ep.(map[string]interface{}); ok {
					if r, ok := em["engagementPanelSectionListRenderer"].(map[string]interface{}); ok {
						if hdr, ok := r["header"].(map[string]interface{}); ok {
							if h, ok := hdr["engagementPanelTitleHeaderRenderer"].(map[string]interface{}); ok {
								if t := getText(h["title"]); strings.Contains(strings.ToLower(t), "comment") {
									// try to find count in title or subtitle
									res.CommentsCountText = t
								}
							}
						}
					}
				}
			}
		}
	}
}

func parseCompactVideoRenderer(v interface{}) *VideoMetadata {
	m := asMap(v)
	if m == nil {
		return nil
	}
	id, _ := m["videoId"].(string)
	if id == "" {
		return nil
	}
	title := getText(m["title"])
	author := getText(m["longBylineText"])
	if author == "" {
		author = getText(m["shortBylineText"])
	}
	channelID := ""
	if lbt, ok := m["longBylineText"].(map[string]interface{}); ok {
		if runs, ok := lbt["runs"].([]interface{}); ok && len(runs) > 0 {
			if rm, ok := runs[0].(map[string]interface{}); ok {
				if ne, ok := rm["navigationEndpoint"].(map[string]interface{}); ok {
					if be, ok := ne["browseEndpoint"].(map[string]interface{}); ok {
						channelID, _ = be["browseId"].(string)
					}
				}
			}
		}
	}
	viewCountText := getText(m["viewCountText"])
	publishedText := getText(m["publishedTimeText"])
	durationText := getText(m["lengthText"])
	if durationText == "" {
		durationText = getText(m["lengthText"])
	}
	// thumbnail
	thumbs := parseThumbnails(m["thumbnail"])
	thumbURL := ""
	if len(thumbs) > 0 {
		thumbURL = thumbs[len(thumbs)-1].URL
	} else {
		thumbURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", id)
		thumbs = []Thumbnail{{URL: thumbURL, Width: 480, Height: 360}}
	}
	// channel avatar not in compactVideo
	isLive := false
	if b, ok := m["isLive"].(bool); ok && b {
		isLive = true
	}
	if arr, ok := m["badges"].([]interface{}); ok {
		for _, b := range arr {
			if bm, ok := b.(map[string]interface{}); ok {
				if r, ok := bm["metadataBadgeRenderer"].(map[string]interface{}); ok {
					if label, ok := r["label"].(string); ok && strings.EqualFold(label, "LIVE") {
						isLive = true
					}
				}
			}
		}
	}
	viewCount := parseViewCountRaw(viewCountText)
	durationSecs := parseDuration(durationText)
	return &VideoMetadata{
		ID:                 id,
		Title:              title,
		Author:             author,
		ViewCount:          viewCount,
		ViewCountText:      viewCountText,
		PublishedTimeText:  publishedText,
		DurationText:       durationText,
		DurationSecs:       durationSecs,
		ThumbnailURL:       thumbURL,
		Thumbnails:         thumbs,
		ChannelID:          channelID,
		IsLive:             isLive,
	}
}

func parseLockupToVideo(v interface{}) *VideoMetadata {
	m := asMap(v)
	if m == nil {
		return nil
	}
	contentId, _ := m["contentId"].(string)
	if contentId == "" {
		return nil
	}
	if ct, ok := m["contentType"].(string); ok && ct != "" && ct != "LOCKUP_CONTENT_TYPE_VIDEO" {
		return nil
	}
	title := ""
	if meta, ok := m["metadata"].(map[string]interface{}); ok {
		if lvm, ok := meta["lockupMetadataViewModel"].(map[string]interface{}); ok {
			if t, ok := lvm["title"].(map[string]interface{}); ok {
				title, _ = t["content"].(string)
			}
		}
	}
	channelName := ""
	channelId := ""
	viewCountText := ""
	publishedText := ""
	durationText := ""
	var thumbs []Thumbnail
	var thumbURL string
	if ci, ok := m["contentImage"].(map[string]interface{}); ok {
		if tvm, ok := ci["thumbnailViewModel"].(map[string]interface{}); ok {
			if img, ok := tvm["image"].(map[string]interface{}); ok {
				ths := parseSourcesThumbnails(img)
				if len(ths) > 0 {
					thumbs = ths
					thumbURL = ths[len(ths)-1].URL
				}
			}
			if overlays, ok := tvm["overlays"].([]interface{}); ok {
				for _, ov := range overlays {
					if om, ok := ov.(map[string]interface{}); ok {
						if tb, ok := om["thumbnailOverlayBadgeViewModel"].(map[string]interface{}); ok {
							if badges, ok := tb["thumbnailBadges"].([]interface{}); ok {
								for _, b := range badges {
									if bm, ok := b.(map[string]interface{}); ok {
										if tbvm, ok := bm["thumbnailBadgeViewModel"].(map[string]interface{}); ok {
											if txt, ok := tbvm["text"].(string); ok && strings.Contains(txt, ":") {
												durationText = txt
											}
										}
									}
								}
							}
						}
						if tb2, ok := om["thumbnailBottomOverlayViewModel"].(map[string]interface{}); ok {
							if badges, ok := tb2["badges"].([]interface{}); ok {
								for _, b := range badges {
									if bm, ok := b.(map[string]interface{}); ok {
										if tbvm, ok := bm["thumbnailBadgeViewModel"].(map[string]interface{}); ok {
											if txt, ok := tbvm["text"].(string); ok && strings.Contains(txt, ":") {
												durationText = txt
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
	if meta, ok := m["metadata"].(map[string]interface{}); ok {
		if lvm, ok := meta["lockupMetadataViewModel"].(map[string]interface{}); ok {
			if md, ok := lvm["metadata"].(map[string]interface{}); ok {
				if cm, ok := md["contentMetadataViewModel"].(map[string]interface{}); ok {
					if rows, ok := cm["metadataRows"].([]interface{}); ok {
						for rowIdx, r := range rows {
							if rm, ok := r.(map[string]interface{}); ok {
								if parts, ok := rm["metadataParts"].([]interface{}); ok {
									for partIdx, p := range parts {
										if pm, ok := p.(map[string]interface{}); ok {
											if txt, ok := pm["text"].(map[string]interface{}); ok {
												if c, ok := txt["content"].(string); ok {
													low := strings.ToLower(c)
													// row0 single part is always channel
													if rowIdx == 0 && partIdx == 0 && channelName == "" {
														channelName = c
														if runs, ok := txt["commandRuns"].([]interface{}); ok && len(runs) > 0 {
															if r0, ok := runs[0].(map[string]interface{}); ok {
																if tap, ok := r0["onTap"].(map[string]interface{}); ok {
																	if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
																		if be, ok := cmd["browseEndpoint"].(map[string]interface{}); ok {
																			channelId, _ = be["browseId"].(string)
																		}
																	}
																}
															}
														}
														continue
													}
													// row1 part0 is views (may be just "269K" or "8.3M" without suffix), part1 is published
													if rowIdx == 1 {
														if partIdx == 0 && viewCountText == "" {
															viewCountText = c
															continue
														}
														if partIdx == 1 && publishedText == "" {
															publishedText = c
															continue
														}
													}
													if strings.Contains(low, "view") || strings.Contains(low, "watching") {
														viewCountText = c
													} else if strings.Contains(low, "ago") || strings.Contains(low, "streamed") || strings.Contains(low, "premiered") {
														publishedText = c
													} else if strings.Contains(c, ":") && durationText == "" {
														durationText = c
													} else if channelName == "" && !strings.Contains(low, "view") && !strings.Contains(low, "ago") {
														channelName = c
														if runs, ok := txt["commandRuns"].([]interface{}); ok && len(runs) > 0 {
															if r0, ok := runs[0].(map[string]interface{}); ok {
																if tap, ok := r0["onTap"].(map[string]interface{}); ok {
																	if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
																		if be, ok := cmd["browseEndpoint"].(map[string]interface{}); ok {
																			channelId, _ = be["browseId"].(string)
																		}
																	}
																}
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
	if title == "" {
		title = contentId
	}
	if thumbURL == "" {
		thumbURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", contentId)
		thumbs = []Thumbnail{{URL: thumbURL, Width: 480, Height: 360}}
	}
	return &VideoMetadata{
		ID:                contentId,
		Title:             title,
		Author:            channelName,
		ViewCountText:     viewCountText,
		PublishedTimeText: publishedText,
		DurationText:      durationText,
		DurationSecs:      parseDuration(durationText),
		ThumbnailURL:      thumbURL,
		Thumbnails:        thumbs,
		ChannelID:         channelId,
		ViewCount:         parseViewCountRaw(viewCountText),
	}
}

func hasDirectUrls(j map[string]interface{}) bool {
	sd, ok := j["streamingData"].(map[string]interface{})
	if !ok {
		return false
	}
	if arr, ok := sd["adaptiveFormats"].([]interface{}); ok {
		c := 0
		for _, e := range arr {
			if em, ok := e.(map[string]interface{}); ok {
				if u, ok := em["url"].(string); ok && u != "" {
					c++
				}
			}
		}
		if c >= 3 {
			return true
		}
	}
	if arr, ok := sd["formats"].([]interface{}); ok {
		for _, e := range arr {
			if em, ok := e.(map[string]interface{}); ok {
				if u, ok := em["url"].(string); ok && u != "" {
					return true
				}
			}
		}
	}
	return false
}

// FetchVideoDetail merges player + next for exhaustive metadata
func FetchVideoDetail(session *InnertubeSession, videoId string) (*VideoDetailResult, error) {
	if strings.TrimSpace(videoId) == "" {
		return nil, fmt.Errorf("videoId empty")
	}
	res := &VideoDetailResult{VideoID: videoId}

	// Fast path: VISIONOS client gives direct URLs without cipher/n, 98 formats (Flow technique)
	playerJSON, err := fetchPlayerVisionOS(session, videoId)
	playerErr := err
	useVisionOS := false
	if err == nil && hasDirectUrls(playerJSON) {
		parsePlayerDetail(playerJSON, res)
		useVisionOS = true
	} else {
		// fallback to WEB (ciphered but still usable for progressive)
		playerJSON2, err2 := fetchPlayer(session, videoId)
		if err2 == nil {
			// if VISIONOS failed but WEB succeeded, parse WEB; if VISIONOS had partial but no direct urls, re-parse WEB over same res (WEB may have cipher but progressive url ok)
			if !useVisionOS {
				// clear previous partial if any? keep VISIONOS metadata but merge WEB streaming if better
				parsePlayerDetail(playerJSON2, res)
			} else {
				// VISIONOS already parsed, but ensure WEB streaming fallback merged if missing
				tmp := &VideoDetailResult{}
				parsePlayerDetail(playerJSON2, tmp)
				if len(res.Formats) == 0 && len(tmp.Formats) > 0 {
					res.Formats = tmp.Formats
				}
				if len(res.AdaptiveFormats) == 0 && len(tmp.AdaptiveFormats) > 0 {
					// keep ciphered adaptives as fallback (will be handled via NewPipe fallback in Kotlin)
					res.AdaptiveFormats = tmp.AdaptiveFormats
				}
			}
			playerErr = nil
		} else if !useVisionOS {
			playerErr = err2
		}
	}

	// Fetch next for channel header, description, related
	nextJSON, err := fetchNext(session, videoId)
	if err != nil {
		// if player succeeded, return player-only result; otherwise fail
		if playerErr != nil {
			return nil, fmt.Errorf("player: %v; next: %w", playerErr, err)
		}
		// player-only fallback
		if res.Title == "" && res.VideoID == "" {
			return nil, fmt.Errorf("next: %w", err)
		}
		return res, nil
	}
	parseNextDetail(nextJSON, res)

	// Ensure Title fallback from player/next
	if res.Title == "" && res.VideoID == "" {
		res.VideoID = videoId
	}
	// Fill channelTitle if still empty from author
	if res.ChannelTitle == "" && res.Author != "" {
		res.ChannelTitle = res.Author
	}
	// Ensure thumbnails from player videoDetails if still empty
	if res.ThumbnailURL == "" && len(res.Thumbnails) == 0 && res.VideoID != "" {
		res.ThumbnailURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", res.VideoID)
		res.Thumbnails = []Thumbnail{{URL: res.ThumbnailURL, Width: 480, Height: 360}}
	}
	// Ensure canonical URL
	if res.CanonicalURL == "" && res.VideoID != "" {
		res.CanonicalURL = "https://www.youtube.com/watch?v=" + res.VideoID
	}
	if res.EmbedURL == "" && res.VideoID != "" {
		res.EmbedURL = "https://www.youtube.com/embed/" + res.VideoID
	}

	return res, nil
}
