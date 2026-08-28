package wediyo

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

func parseCourseVideo(m interface{}, idx int) *CourseVideo {
	// Course uses same lockupViewModel as playlist videos (4 qualities 168..336)
	pv := parsePlaylistVideo(m, idx)
	if pv == nil {
		return nil
	}
	return &CourseVideo{
		VideoId:           pv.VideoId,
		Title:             pv.Title,
		ChannelName:       pv.ChannelName,
		ChannelId:         pv.ChannelId,
		ThumbnailURL:      pv.ThumbnailURL,
		Thumbnails:        pv.Thumbnails,
		DurationText:      pv.DurationText,
		IndexText:         pv.IndexText,
		ViewCountText:     pv.ViewCountText,
		PublishedTimeText: pv.PublishedTimeText,
		IsUnavailable:     pv.IsUnavailable,
		UnavailableReason: pv.UnavailableReason,
	}
}

func parseCourseHeader(j map[string]interface{}) *CourseHeader {
	h := &CourseHeader{}
	// header playlistHeaderRenderer (course)
	if hdr, ok := j["header"].(map[string]interface{}); ok {
		if p, ok := hdr["playlistHeaderRenderer"].(map[string]interface{}); ok {
			if t, ok := p["title"].(map[string]interface{}); ok {
				// may be missing; try getText
				if txt := getText(t); txt != "" {
					h.Title = txt
				}
			}
			if num, ok := p["numVideosText"]; ok {
				h.VideoCountText = getText(num)
				if h.VideoCountText == "" {
					if st, ok := num.(map[string]interface{}); ok {
						if s, ok := st["simpleText"].(string); ok {
							h.VideoCountText = s
						}
					}
				}
				var digits strings.Builder
				for _, ch := range h.VideoCountText {
					if ch >= '0' && ch <= '9' {
						digits.WriteRune(ch)
					}
				}
				fmt.Sscan(digits.String(), &h.VideoCount)
			}
			if hdr["playlistHeaderRenderer"] != nil {
				if phb, ok := p["playlistHeaderBanner"]; ok {
					if hb, ok := phb.(map[string]interface{}); ok {
						if hpt, ok := hb["heroPlaylistThumbnailRenderer"].(map[string]interface{}); ok {
							if th, ok := hpt["thumbnail"]; ok {
								ths := parseThumbnails(th)
								if len(ths) > 0 {
									h.Thumbnails = ths
									h.ThumbnailURL = ths[len(ths)-1].URL
								}
							}
						}
					}
				}
				if cin, ok := p["cinematicContainer"]; ok {
					if cr, ok := cin.(map[string]interface{}); ok {
						if ccr, ok := cr["cinematicContainerRenderer"].(map[string]interface{}); ok {
							if bic, ok := ccr["backgroundImageConfig"].(map[string]interface{}); ok {
								if th, ok := bic["thumbnail"]; ok {
									ths := parseThumbnails(th)
									if len(ths) > 0 && len(h.Thumbnails) == 0 {
										h.Thumbnails = ths
										h.ThumbnailURL = ths[len(ths)-1].URL
									}
								}
							}
						}
					}
				}
			}
			if stats, ok := p["stats"].([]interface{}); ok {
				for _, s := range stats {
					if sm, ok := s.(map[string]interface{}); ok {
						txt := getText(sm)
						if txt == "" {
							if st, ok := sm["simpleText"].(string); ok {
								txt = st
							}
						}
						if strings.Contains(txt, "videos") {
							h.VideoCountText = txt
							var digits strings.Builder
							for _, ch := range txt {
								if ch >= '0' && ch <= '9' {
									digits.WriteRune(ch)
								}
							}
							fmt.Sscan(digits.String(), &h.VideoCount)
						} else if strings.Contains(txt, "views") {
							h.ViewCountText = txt
						}
					}
				}
			}
			if byline, ok := p["byline"].([]interface{}); ok {
				for _, b := range byline {
					if bm, ok := b.(map[string]interface{}); ok {
						if pbr, ok := bm["playlistBylineRenderer"].(map[string]interface{}); ok {
							txt := getText(pbr["text"])
							if strings.Contains(txt, "Last updated") {
								h.LastUpdatedText = txt
							}
						}
					}
				}
			}
			if desc, ok := p["descriptionText"]; ok {
				h.Description = getText(desc)
				if h.Description == "" {
					if st, ok := desc.(map[string]interface{}); ok {
						if s, ok := st["simpleText"].(string); ok {
							h.Description = s
						}
					}
				}
			}
			if own, ok := p["ownerText"]; ok {
				h.ChannelName = getText(own)
				if m, ok := own.(map[string]interface{}); ok {
					if runs, ok := m["runs"].([]interface{}); ok && len(runs) > 0 {
						if r0, ok := runs[0].(map[string]interface{}); ok {
							if ep, ok := r0["navigationEndpoint"].(map[string]interface{}); ok {
								if be, ok := ep["browseEndpoint"].(map[string]interface{}); ok {
									h.ChannelId, _ = be["browseId"].(string)
									h.ChannelHandle, _ = be["canonicalBaseUrl"].(string)
								}
							}
						}
					}
				}
			}
		}
	}
	if meta, ok := j["metadata"].(map[string]interface{}); ok {
		if pm, ok := meta["playlistMetadataRenderer"].(map[string]interface{}); ok {
			if t, ok := pm["title"].(string); ok && h.Title == "" {
				h.Title = t
			}
			if d, ok := pm["description"].(string); ok && h.Description == "" {
				h.Description = d
			}
		}
	}
	if sidebar, ok := j["sidebar"].(map[string]interface{}); ok {
		if psr, ok := sidebar["playlistSidebarRenderer"].(map[string]interface{}); ok {
			if items, ok := psr["items"].([]interface{}); ok {
				for _, it := range items {
					if im, ok := it.(map[string]interface{}); ok {
						if pri, ok := im["playlistSidebarPrimaryInfoRenderer"].(map[string]interface{}); ok {
							if h.Title == "" {
								h.Title = getText(pri["title"])
							}
							if h.Description == "" {
								h.Description = getText(pri["description"])
							}
							if thr, ok := pri["thumbnailRenderer"].(map[string]interface{}); ok {
								if pvt, ok := thr["playlistVideoThumbnailRenderer"].(map[string]interface{}); ok {
									if th, ok := pvt["thumbnail"]; ok {
										ths := parseThumbnails(th)
										if len(ths) > 0 {
											h.Thumbnails = ths
											h.ThumbnailURL = ths[len(ths)-1].URL
										}
									}
								}
							}
							if stats, ok := pri["stats"].([]interface{}); ok {
								for _, s := range stats {
									if sm, ok := s.(map[string]interface{}); ok {
										txt := getText(sm)
										if txt == "" {
											if st, ok := sm["simpleText"].(string); ok {
												txt = st
											}
										}
										if strings.Contains(txt, "videos") {
											h.VideoCountText = txt
											var digits strings.Builder
											for _, ch := range txt {
												if ch >= '0' && ch <= '9' {
													digits.WriteRune(ch)
												}
											}
											fmt.Sscan(digits.String(), &h.VideoCount)
										} else if strings.Contains(txt, "views") {
											h.ViewCountText = txt
										} else if strings.Contains(txt, "Last updated") {
											h.LastUpdatedText = txt
										}
									}
								}
							}
						}
						if sec, ok := im["playlistSidebarSecondaryInfoRenderer"].(map[string]interface{}); ok {
							if vo, ok := sec["videoOwner"].(map[string]interface{}); ok {
								if vor, ok := vo["videoOwnerRenderer"].(map[string]interface{}); ok {
									if h.ChannelName == "" {
										h.ChannelName = getText(vor["title"])
									}
									if th, ok := vor["thumbnail"]; ok {
										ths := parseThumbnails(th)
										if len(ths) > 0 {
											h.ChannelAvatars = ths
											h.ChannelAvatarURL = ths[len(ths)-1].URL
										}
									}
									if ep, ok := vor["navigationEndpoint"]; ok {
										if em, ok := ep.(map[string]interface{}); ok {
											if be, ok := em["browseEndpoint"].(map[string]interface{}); ok {
												if h.ChannelId == "" {
													h.ChannelId, _ = be["browseId"].(string)
												}
												if h.ChannelHandle == "" {
													h.ChannelHandle, _ = be["canonicalBaseUrl"].(string)
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
	if alerts, ok := j["alerts"].([]interface{}); ok {
		for _, a := range alerts {
			if am, ok := a.(map[string]interface{}); ok {
				if awb, ok := am["alertWithButtonRenderer"].(map[string]interface{}); ok {
					if txt, ok := awb["text"]; ok {
						if s := getText(txt); strings.Contains(strings.ToLower(s), "unavailable") {
							h.HasUnavailable = true
						}
					}
				}
			}
		}
	}
	return h
}

func parseCourseVideos(j map[string]interface{}) ([]CourseVideo, string) {
	vids, cont := parsePlaylistVideos(j)
	out := make([]CourseVideo, 0, len(vids))
	for i, v := range vids {
		if cv := parseCourseVideo(map[string]interface{}{
			"contentId":   v.VideoId,
			"contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
			"contentImage": map[string]interface{}{
				"thumbnailViewModel": map[string]interface{}{
					"image": map[string]interface{}{
						"sources": func() []interface{} {
							var arr []interface{}
							for _, th := range v.Thumbnails {
								arr = append(arr, map[string]interface{}{"url": th.URL, "width": float64(th.Width), "height": float64(th.Height)})
							}
							return arr
						}(),
					},
					"overlays": []interface{}{
						map[string]interface{}{
							"thumbnailBottomOverlayViewModel": map[string]interface{}{
								"badges": []interface{}{
									map[string]interface{}{
										"thumbnailBadgeViewModel": map[string]interface{}{"text": v.DurationText},
									},
								},
							},
						},
					},
				},
			},
			"metadata": map[string]interface{}{
				"lockupMetadataViewModel": map[string]interface{}{
					"title": map[string]interface{}{"content": v.Title},
					"metadata": map[string]interface{}{
						"contentMetadataViewModel": map[string]interface{}{
							"metadataRows": []interface{}{
								map[string]interface{}{"metadataParts": []interface{}{map[string]interface{}{"text": map[string]interface{}{"content": v.ChannelName}}}},
								map[string]interface{}{"metadataParts": []interface{}{map[string]interface{}{"text": map[string]interface{}{"content": v.ViewCountText}}, map[string]interface{}{"text": map[string]interface{}{"content": v.PublishedTimeText}}}},
							},
						},
					},
				},
			},
		}, i); cv != nil {
			// restore original
			cv.VideoId = v.VideoId
			cv.Title = v.Title
			cv.Thumbnails = v.Thumbnails
			cv.ThumbnailURL = v.ThumbnailURL
			cv.DurationText = v.DurationText
			cv.ChannelName = v.ChannelName
			cv.ChannelId = v.ChannelId
			cv.ViewCountText = v.ViewCountText
			cv.PublishedTimeText = v.PublishedTimeText
			cv.IsUnavailable = v.IsUnavailable
			cv.UnavailableReason = v.UnavailableReason
			cv.IndexText = v.IndexText
			out = append(out, *cv)
		}
	}
	// If we reconstructed, better just directly map
	if len(out) != len(vids) {
		out = make([]CourseVideo, 0, len(vids))
		for _, v := range vids {
			out = append(out, CourseVideo{
				VideoId: v.VideoId, Title: v.Title, ChannelName: v.ChannelName, ChannelId: v.ChannelId,
				ThumbnailURL: v.ThumbnailURL, Thumbnails: v.Thumbnails, DurationText: v.DurationText,
				IndexText: v.IndexText, ViewCountText: v.ViewCountText, PublishedTimeText: v.PublishedTimeText,
				IsUnavailable: v.IsUnavailable, UnavailableReason: v.UnavailableReason,
			})
		}
	}
	return out, cont
}

func collectCourseDetail(j map[string]interface{}) (*CourseDetailResult, error) {
	header := parseCourseHeader(j)
	videos, cont := parsePlaylistVideos(j) // reuse playlist parser (same structure: lockupViewModel)
	// Map to CourseVideo
	cVideos := make([]CourseVideo, 0, len(videos))
	for _, v := range videos {
		cVideos = append(cVideos, CourseVideo{
			VideoId: v.VideoId, Title: v.Title, ChannelName: v.ChannelName, ChannelId: v.ChannelId,
			ThumbnailURL: v.ThumbnailURL, Thumbnails: v.Thumbnails, DurationText: v.DurationText,
			IndexText: v.IndexText, ViewCountText: v.ViewCountText, PublishedTimeText: v.PublishedTimeText,
			IsUnavailable: v.IsUnavailable, UnavailableReason: v.UnavailableReason,
		})
	}
	playlistId := ""
	if meta, ok := j["metadata"].(map[string]interface{}); ok {
		if pm, ok := meta["playlistMetadataRenderer"].(map[string]interface{}); ok {
			if link, ok := pm["androidAppindexingLink"].(string); ok {
				if idx := strings.Index(link, "list="); idx != -1 {
					playlistId = link[idx+5:]
					if amp := strings.Index(playlistId, "&"); amp != -1 {
						playlistId = playlistId[:amp]
					}
				}
			}
		}
	}
	// fallback via watchEndpoint
	if playlistId == "" {
		if sidebar, ok := j["sidebar"].(map[string]interface{}); ok {
			if psr, ok := sidebar["playlistSidebarRenderer"].(map[string]interface{}); ok {
				if items, ok := psr["items"].([]interface{}); ok && len(items) > 0 {
					if im, ok := items[0].(map[string]interface{}); ok {
						if pri, ok := im["playlistSidebarPrimaryInfoRenderer"].(map[string]interface{}); ok {
							if t, ok := pri["title"].(map[string]interface{}); ok {
								if runs, ok := t["runs"].([]interface{}); ok && len(runs) > 0 {
									if r0, ok := runs[0].(map[string]interface{}); ok {
										if ep, ok := r0["navigationEndpoint"]; ok {
											if em, ok := ep.(map[string]interface{}); ok {
												if we, ok := em["watchEndpoint"]; ok {
													if wem, ok := we.(map[string]interface{}); ok {
														playlistId, _ = wem["playlistId"].(string)
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
	// handle continuation via onResponseReceived actions (course pagination)
	if len(cVideos) == 0 {
		var all []CourseVideo
		contTmp := cont
		if acts, ok := j["onResponseReceivedActions"].([]interface{}); ok {
			for _, a := range acts {
				if am, ok := a.(map[string]interface{}); ok {
					if appendAct, ok := am["appendContinuationItemsAction"].(map[string]interface{}); ok {
						if items, ok := appendAct["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if tok := extractContinuationToken(it); tok != "" {
									contTmp = tok
									continue
								}
								if im, ok := it.(map[string]interface{}); ok {
									if _, hasLockup := im["lockupViewModel"]; hasLockup {
										if v := parsePlaylistVideo(im, len(all)); v != nil {
											all = append(all, CourseVideo{
												VideoId: v.VideoId, Title: v.Title, ChannelName: v.ChannelName, ChannelId: v.ChannelId,
												ThumbnailURL: v.ThumbnailURL, Thumbnails: v.Thumbnails, DurationText: v.DurationText,
												IndexText: fmt.Sprintf("%d", len(all)+1), ViewCountText: v.ViewCountText, PublishedTimeText: v.PublishedTimeText,
											})
										}
									}
								}
							}
						}
					}
				}
			}
			if len(all) > 0 || contTmp != "" {
				if len(all) > 0 {
					cVideos = all
				}
				cont = contTmp
			}
		}
	}
	if cont == "" {
		cont = extractContinuationToken(j)
	}
	return &CourseDetailResult{Header: header, Videos: cVideos, Continuation: cont, PlaylistId: playlistId}, nil
}

func FetchCourse(session *InnertubeSession, playlistId string, continuation string) (*CourseDetailResult, error) {
	return FetchCourseWithOption(session, playlistId, continuation, true)
}

func FetchCourseWithOption(session *InnertubeSession, playlistId string, continuation string, showUnavailable bool) (*CourseDetailResult, error) {
	if strings.TrimSpace(playlistId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("playlistId and continuation empty")
	}
	pid := strings.TrimSpace(playlistId)
	if strings.HasPrefix(pid, "VL") {
		pid = strings.TrimPrefix(pid, "VL")
	}
	browseId := "VL" + pid
	if strings.HasPrefix(playlistId, "VL") {
		browseId = playlistId
		pid = strings.TrimPrefix(playlistId, "VL")
	}
	client := &http.Client{Timeout: 15 * time.Second}
	urlStr := fmt.Sprintf("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false&key=%s", session.APIKey)
	tz := "UTC"
	if idx := strings.Index(session.Pref, "tz="); idx != -1 {
		rest := session.Pref[idx+3:]
		if amp := strings.Index(rest, "&"); amp != -1 {
			tz = rest[:amp]
		} else {
			tz = rest
		}
	}
	originalURL := "https://www.youtube.com/playlist?list=" + pid
	context := map[string]interface{}{
		"client": map[string]interface{}{
			"hl": "en", "gl": "IN", "remoteHost": "", "deviceMake": "", "deviceModel": "",
			"visitorData": session.VisitorData, "userAgent": userAgent + ",gzip(gfe)", "clientName": session.ClientName, "clientVersion": session.ClientVersion,
			"osName": "Windows", "osVersion": "10.0", "originalUrl": originalURL, "screenPixelDensity": 2, "platform": "DESKTOP", "clientFormFactor": "UNKNOWN_FORM_FACTOR",
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
	bodyMap := map[string]interface{}{"context": context}
	if strings.TrimSpace(continuation) != "" {
		bodyMap["continuation"] = continuation
	} else {
		bodyMap["browseId"] = browseId
		if showUnavailable {
			bodyMap["params"] = "wgYCCAA="
		}
	}
	bodyBytes, _ := json.Marshal(bodyMap)
	req, err := http.NewRequest("POST", urlStr, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Origin", "https://www.youtube.com")
	req.Header.Set("Referer", originalURL)
	req.Header.Set("X-Goog-Visitor-Id", session.VisitorData)
	req.Header.Set("X-Youtube-Client-Name", "1")
	req.Header.Set("X-Youtube-Client-Version", session.ClientVersion)
	req.Header.Set("X-Youtube-Bootstrap-Logged-In", "false")
	req.Header.Set("Cookie", session.CookieHeader)
	req.Header.Set("User-Agent", userAgent)
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("browse POST: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		buf := new(bytes.Buffer)
		buf.ReadFrom(resp.Body)
		s := buf.String()
		if len(s) > 500 {
			s = s[:500]
		}
		return nil, fmt.Errorf("browse status %d: %s", resp.StatusCode, s)
	}
	var j map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&j); err != nil {
		return nil, fmt.Errorf("parse browse json: %w", err)
	}
	return collectCourseDetail(j)
}
