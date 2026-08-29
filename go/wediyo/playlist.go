package wediyo

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

func parsePlaylistVideo(m interface{}, index int) *PlaylistVideo {
	lm, ok := m.(map[string]interface{})
	if !ok {
		return nil
	}
	// lockupViewModel expected, but caller passes lockupViewModel directly or wrapper
	// Support both: if m has contentId, it's already lockupViewModel; else if m has lockupViewModel, unwrap
	if _, hasContentId := lm["contentId"]; !hasContentId {
		if inner, ok := lm["lockupViewModel"].(map[string]interface{}); ok {
			lm = inner
		} else {
			// try to handle playlistVideoRenderer directly
			if pvr, ok := lm["playlistVideoRenderer"].(map[string]interface{}); ok {
				// fallback for old style
				vid, _ := pvr["videoId"].(string)
				title := getText(pvr["title"])
				if title == "" {
					title, _ = pvr["title"].(string)
				}
				isUnav := false
				reason := ""
				if s, ok := pvr["isPlayable"].(bool); ok && !s {
					isUnav = true
				}
				if title == "[Private video]" || title == "[Deleted video]" {
					isUnav = true
					reason = title
				}
				thumb := ""
				var thumbs []Thumbnail
				if th, ok := pvr["thumbnail"]; ok {
					ths := parseThumbnails(th)
					if len(ths) > 0 {
						thumbs = ths
						thumb = ths[len(ths)-1].URL
					}
				}
				return &PlaylistVideo{VideoId: vid, Title: title, ThumbnailURL: thumb, Thumbnails: thumbs, IsUnavailable: isUnav, UnavailableReason: reason, IndexText: fmt.Sprintf("%d", index+1)}
			}
			return nil
		}
	}
	contentId, _ := lm["contentId"].(string)
	if contentId == "" {
		// for unavailable, contentId may still be videoId but hidden? try to get from elsewhere
		// still return placeholder
	}
	ctype, _ := lm["contentType"].(string)
	if ctype != "" && ctype != "LOCKUP_CONTENT_TYPE_VIDEO" {
		// allow video only; but for unavailable, ctype may still be video
		// if not video, skip
		// but for playlist, we expect video
		if ctype != "LOCKUP_CONTENT_TYPE_VIDEO" {
			// still try to parse if title indicates private
		}
	}
	title := ""
	channelName := ""
	channelId := ""
	viewCountText := ""
	publishedText := ""
	durationText := ""
	var thumbs []Thumbnail
	var thumbURL string
	isUnavailable := false
	reason := ""

	if ci, ok := lm["contentImage"].(map[string]interface{}); ok {
		if tv, ok := ci["thumbnailViewModel"].(map[string]interface{}); ok {
			if img, ok := tv["image"].(map[string]interface{}); ok {
				ths := parseSourcesThumbnails(img)
				if len(ths) > 0 {
					thumbs = ths
					thumbURL = ths[len(ths)-1].URL
				}
			}
			if overlays, ok := tv["overlays"].([]interface{}); ok {
				for _, ov := range overlays {
					if om, ok := ov.(map[string]interface{}); ok {
						if bot, ok := om["thumbnailBottomOverlayViewModel"].(map[string]interface{}); ok {
							if badgesArr, ok := bot["badges"].([]interface{}); ok {
								for _, b := range badgesArr {
									if bm, ok := b.(map[string]interface{}); ok {
										if tb, ok := bm["thumbnailBadgeViewModel"].(map[string]interface{}); ok {
											if txt, ok := tb["text"].(string); ok && txt != "" {
												if strings.Contains(txt, ":") {
													durationText = txt
												}
											}
										}
									}
								}
							}
						}
						if top, ok := om["thumbnailOverlaySidePanelRenderer"]; ok {
							_ = top
						}
					}
				}
			}
		}
	}
	if meta, ok := lm["metadata"].(map[string]interface{}); ok {
		if lockup, ok := meta["lockupMetadataViewModel"].(map[string]interface{}); ok {
			if t, ok := lockup["title"].(map[string]interface{}); ok {
				if c, ok := t["content"].(string); ok {
					title = c
				} else {
					title = getText(t)
				}
			}
			if img, ok := lockup["image"].(map[string]interface{}); ok {
				_ = img
			}
			if md, ok := lockup["metadata"].(map[string]interface{}); ok {
				if cm, ok := md["contentMetadataViewModel"].(map[string]interface{}); ok {
					if rows, ok := cm["metadataRows"].([]interface{}); ok {
						// row 0: channel, row 1: views/published
						if len(rows) > 0 {
							if r0, ok := rows[0].(map[string]interface{}); ok {
								if parts, ok := r0["metadataParts"].([]interface{}); ok && len(parts) > 0 {
									if p0, ok := parts[0].(map[string]interface{}); ok {
										if txt, ok := p0["text"].(map[string]interface{}); ok {
											if c, ok := txt["content"].(string); ok {
												channelName = c
											}
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
						if len(rows) > 1 {
							if r1, ok := rows[1].(map[string]interface{}); ok {
								if parts, ok := r1["metadataParts"].([]interface{}); ok {
									for _, p := range parts {
										if pm, ok := p.(map[string]interface{}); ok {
											if txt, ok := pm["text"].(map[string]interface{}); ok {
												if c, ok := txt["content"].(string); ok {
													if strings.Contains(strings.ToLower(c), "views") {
														viewCountText = c
													} else if strings.Contains(c, "ago") || strings.Contains(c, "Streamed") {
														publishedText = c
													} else if viewCountText == "" {
														viewCountText = c
													} else {
														publishedText = c
													}
												}
											}
										}
									}
								}
							}
						}
						// check for unavailable in rows
						for _, r := range rows {
							if rm, ok := r.(map[string]interface{}); ok {
								if s := strings.ToLower(getText(rm)); strings.Contains(s, "private") || strings.Contains(s, "deleted") || strings.Contains(s, "unavailable") {
									isUnavailable = true
									reason = getText(rm)
								}
							}
						}
					}
				}
			}
		}
	}
	// fallback title checks
	if title == "[Private video]" || title == "[Deleted video]" || strings.EqualFold(title, "Private video") || strings.EqualFold(title, "Deleted video") {
		isUnavailable = true
		reason = title
	}
	if strings.Contains(strings.ToLower(title), "unavailable") {
		isUnavailable = true
		if reason == "" {
			reason = title
		}
	}
	// check for isUnplayable flag in raw
	if v, ok := lm["isUnplayable"].(bool); ok && v {
		isUnavailable = true
	}
	if v, ok := lm["isUnavailable"].(bool); ok && v {
		isUnavailable = true
	}
	// thumbnail may be placeholder for unavailable
	if thumbURL == "" && contentId != "" {
		thumbURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", contentId)
		thumbs = []Thumbnail{{URL: thumbURL, Width: 480, Height: 360}}
	}
	if isUnavailable {
		// ensure we have reason
		if reason == "" {
			reason = "Unavailable"
		}
		// maybe dim thumbnail already
	}
	return &PlaylistVideo{
		VideoId:           contentId,
		Title:             title,
		ChannelName:       channelName,
		ChannelId:         channelId,
		ThumbnailURL:      thumbURL,
		Thumbnails:        thumbs,
		DurationText:      durationText,
		IndexText:         fmt.Sprintf("%d", index+1),
		ViewCountText:     viewCountText,
		PublishedTimeText: publishedText,
		IsUnavailable:     isUnavailable,
		UnavailableReason: reason,
	}
}

func parsePlaylistHeader(j map[string]interface{}) *PlaylistHeader {
	h := &PlaylistHeader{}
	// header pageHeaderRenderer
	if hdr, ok := j["header"].(map[string]interface{}); ok {
		if p, ok := hdr["pageHeaderRenderer"].(map[string]interface{}); ok {
			if c, ok := p["content"].(map[string]interface{}); ok {
				if vm, ok := c["pageHeaderViewModel"].(map[string]interface{}); ok {
					if t, ok := vm["title"].(map[string]interface{}); ok {
						if dyn, ok := t["dynamicTextViewModel"].(map[string]interface{}); ok {
							if txt, ok := dyn["text"].(map[string]interface{}); ok {
								h.Title, _ = txt["content"].(string)
							}
						}
					}
				}
			}
			// also try direct pageTitle
			if h.Title == "" {
				if pt, ok := p["pageTitle"].(string); ok {
					h.Title = pt
				}
			}
		}
	}
	// metadata playlistMetadataRenderer
	if meta, ok := j["metadata"].(map[string]interface{}); ok {
		if pm, ok := meta["playlistMetadataRenderer"].(map[string]interface{}); ok {
			if t, ok := pm["title"].(string); ok && h.Title == "" {
				h.Title = t
			}
			if d, ok := pm["description"].(string); ok {
				h.Description = d
			}
		}
	}
	// sidebar
	if sidebar, ok := j["sidebar"].(map[string]interface{}); ok {
		if psr, ok := sidebar["playlistSidebarRenderer"].(map[string]interface{}); ok {
			if items, ok := psr["items"].([]interface{}); ok {
				for _, it := range items {
					if im, ok := it.(map[string]interface{}); ok {
						if pri, ok := im["playlistSidebarPrimaryInfoRenderer"].(map[string]interface{}); ok {
							if h.Title == "" {
								if t, ok := pri["title"]; ok {
									h.Title = getText(t)
								}
							}
							// thumbnail
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
							// stats
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
									if t, ok := vor["title"]; ok {
										h.ChannelName = getText(t)
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
			}
		}
	}
	// alerts for hasUnavailable
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

func parsePlaylistVideos(j map[string]interface{}) ([]PlaylistVideo, string) {
	var videos []PlaylistVideo
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return videos, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return videos, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return videos, continuation
	}
	var secList map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				if c, ok := tr["content"].(map[string]interface{}); ok {
					if sl, ok := c["sectionListRenderer"].(map[string]interface{}); ok {
						secList = sl
						break
					}
				}
			}
		}
	}
	if secList == nil {
		return videos, continuation
	}
	arr, ok := secList["contents"].([]interface{})
	if !ok {
		return videos, continuation
	}
	idx := 0
	for _, sec := range arr {
		if sm, ok := sec.(map[string]interface{}); ok {
			if isr, ok := sm["itemSectionRenderer"].(map[string]interface{}); ok {
				if contents2, ok := isr["contents"].([]interface{}); ok {
					for _, item := range contents2 {
						if tok := extractContinuationToken(item); tok != "" {
							continuation = tok
							continue
						}
						if im, ok := item.(map[string]interface{}); ok {
							if _, hasLockup := im["lockupViewModel"]; hasLockup {
								if v := parsePlaylistVideo(im, idx); v != nil {
									videos = append(videos, *v)
									idx++
								}
							} else if _, hasPvr := im["playlistVideoRenderer"]; hasPvr {
								if v := parsePlaylistVideo(im, idx); v != nil {
									videos = append(videos, *v)
									idx++
								}
							} else if _, ok := im["continuationItemRenderer"]; ok {
								if tok := extractContinuationToken(im); tok != "" {
									continuation = tok
								}
							} else if _, ok := im["continuationItemViewModel"]; ok {
								if tok := extractContinuationToken(im); tok != "" {
									continuation = tok
								}
							}
						}
					}
				}
			} else if tok := extractContinuationToken(sm); tok != "" {
				// sectionList level continuation (course case: 100 videos + continuationItemViewModel, or playlist/shows)
				// Prefer itemSectionRenderer-level token (inner) which contains actual playlist pagination.
				// Outer token at sectionList level is often a reload continuation that returns empty for playlists.
				if continuation == "" {
					continuation = tok
				}
			}
		}
	}
	return videos, continuation
}

func collectPlaylistDetail(j map[string]interface{}) (*PlaylistDetailResult, error) {
	header := parsePlaylistHeader(j)
	videos, continuation := parsePlaylistVideos(j)
	playlistId := ""
	if meta, ok := j["metadata"].(map[string]interface{}); ok {
		if pm, ok := meta["playlistMetadataRenderer"].(map[string]interface{}); ok {
			if link, ok := pm["androidAppindexingLink"].(string); ok {
				// link like android-app://.../playlist?list=PL...
				if idx := strings.Index(link, "list="); idx != -1 {
					playlistId = link[idx+5:]
					if amp := strings.Index(playlistId, "&"); amp != -1 {
						playlistId = playlistId[:amp]
					}
				}
			}
		}
	}
	if playlistId == "" {
		// try to get from sidebar watchEndpoint
		if sidebar, ok := j["sidebar"].(map[string]interface{}); ok {
			if psr, ok := sidebar["playlistSidebarRenderer"].(map[string]interface{}); ok {
				if items, ok := psr["items"].([]interface{}); ok && len(items) > 0 {
					if im, ok := items[0].(map[string]interface{}); ok {
						if pri, ok := im["playlistSidebarPrimaryInfoRenderer"].(map[string]interface{}); ok {
							if thr, ok := pri["thumbnailRenderer"]; ok {
								// not id
								_ = thr
							}
							if title, ok := pri["title"]; ok {
								_ = title
							}
							// try watchEndpoint
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
	// handle continuation pagination via onResponseReceivedActions etc
	if len(videos) == 0 {
		var all []PlaylistVideo
		cont := continuation
		if acts, ok := j["onResponseReceivedActions"].([]interface{}); ok {
			for _, a := range acts {
				if am, ok := a.(map[string]interface{}); ok {
					if appendAct, ok := am["appendContinuationItemsAction"].(map[string]interface{}); ok {
						if items, ok := appendAct["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if tok := extractContinuationToken(it); tok != "" {
									cont = tok
									continue
								}
								if im, ok := it.(map[string]interface{}); ok {
									if _, hasLockup := im["lockupViewModel"]; hasLockup {
										if v := parsePlaylistVideo(im, len(all)); v != nil {
											all = append(all, *v)
										}
									} else if sec, ok := im["itemSectionRenderer"]; ok {
										if sem, ok := sec.(map[string]interface{}); ok {
											if contents2, ok := sem["contents"].([]interface{}); ok {
												for _, sub := range contents2 {
													if sim, ok := sub.(map[string]interface{}); ok {
														if _, hasLockup := sim["lockupViewModel"]; hasLockup {
															if v := parsePlaylistVideo(sim, len(all)); v != nil {
																all = append(all, *v)
															}
														}
													}
													if tok := extractContinuationToken(sub); tok != "" {
														cont = tok
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
			if len(all) > 0 || cont != "" {
				if len(all) > 0 {
					videos = all
				}
				if cont != "" {
					continuation = cont
				}
			}
		}
		if eps, ok := j["onResponseReceivedEndpoints"].([]interface{}); ok && len(videos) == 0 {
			var allE []PlaylistVideo
			contE := continuation
			for _, ep := range eps {
				if em, ok := ep.(map[string]interface{}); ok {
					if appendAct, ok := em["appendContinuationItemsAction"].(map[string]interface{}); ok {
						if items, ok := appendAct["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if tok := extractContinuationToken(it); tok != "" {
									contE = tok
									continue
								}
								if im, ok := it.(map[string]interface{}); ok {
									if _, hasLockup := im["lockupViewModel"]; hasLockup {
										if v := parsePlaylistVideo(im, len(allE)); v != nil {
											allE = append(allE, *v)
										}
									}
								}
							}
						}
					}
				}
			}
			if len(allE) > 0 || contE != "" {
				if len(allE) > 0 {
					videos = allE
				}
				if contE != "" {
					continuation = contE
				}
			}
		}
	}
	if continuation == "" {
		continuation = extractContinuationToken(j)
	}
	return &PlaylistDetailResult{Header: header, Videos: videos, Continuation: continuation, PlaylistId: playlistId}, nil
}

func FetchPlaylist(session *InnertubeSession, playlistId string, continuation string) (*PlaylistDetailResult, error) {
	// default show unavailable
	return FetchPlaylistWithOption(session, playlistId, continuation, true)
}

func FetchPlaylistWithUnavailable(session *InnertubeSession, playlistId string, continuation string) (*PlaylistDetailResult, error) {
	return FetchPlaylistWithOption(session, playlistId, continuation, true)
}

func FetchPlaylistWithOption(session *InnertubeSession, playlistId string, continuation string, showUnavailable bool) (*PlaylistDetailResult, error) {
	if strings.TrimSpace(playlistId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("playlistId and continuation empty")
	}
	// playlistId may be with or without PL prefix, ensure PL
	pid := strings.TrimSpace(playlistId)
	if strings.HasPrefix(pid, "VL") {
		pid = strings.TrimPrefix(pid, "VL")
	}
	if !strings.HasPrefix(pid, "PL") && !strings.HasPrefix(pid, "OL") && !strings.HasPrefix(pid, "UU") {
		// assume PL
	}
	browseId := "VL" + pid
	if strings.HasPrefix(pid, "VL") {
		browseId = pid
		pid = strings.TrimPrefix(pid, "VL")
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
	return collectPlaylistDetail(j)
}
