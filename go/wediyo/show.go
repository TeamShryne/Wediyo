package wediyo

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

func parseShowEpisode(m interface{}, idx int) *ShowEpisode {
	pvr, ok := m.(map[string]interface{})
	if !ok {
		if lm, ok := m.(map[string]interface{}); ok {
			if inner, ok := lm["playlistVideoRenderer"].(map[string]interface{}); ok {
				pvr = inner
			} else {
				return nil
			}
		} else {
			return nil
		}
	}
	// handle wrapper
	if inner, ok := pvr["playlistVideoRenderer"].(map[string]interface{}); ok {
		pvr = inner
	}
	vid, _ := pvr["videoId"].(string)
	if vid == "" {
		return nil
	}
	title := getText(pvr["title"])
	duration := getText(pvr["lengthText"])
	if duration == "" {
		if lt, ok := pvr["lengthText"].(map[string]interface{}); ok {
			if s, ok := lt["simpleText"].(string); ok {
				duration = s
			}
		}
	}
	secs := 0
	if s, ok := pvr["lengthSeconds"].(string); ok {
		fmt.Sscan(s, &secs)
	} else if f, ok := pvr["lengthSeconds"].(float64); ok {
		secs = int(f)
	}
	if secs == 0 && duration != "" {
		secs = int(parseDuration(duration))
	}
	indexText := getText(pvr["index"])
	if indexText == "" {
		if idxMap, ok := pvr["index"].(map[string]interface{}); ok {
			if s, ok := idxMap["simpleText"].(string); ok {
				indexText = s
			}
		}
	}
	if indexText == "" {
		indexText = fmt.Sprintf("%d", idx+1)
	}
	var thumbs []Thumbnail
	thumbURL := ""
	if th, ok := pvr["thumbnail"]; ok {
		ths := parseThumbnails(th)
		if len(ths) > 0 {
			thumbs = ths
			thumbURL = ths[len(ths)-1].URL
		}
	}
	isUnplayable := false
	if b, ok := pvr["isPlayable"].(bool); ok && !b {
		isUnplayable = true
	}
	episodeLabel := ""
	// try to get from thumbnailOverlays? not needed
	_ = episodeLabel
	return &ShowEpisode{
		VideoId:      vid,
		Title:        title,
		ThumbnailURL: thumbURL,
		Thumbnails:   thumbs,
		DurationText: duration,
		DurationSecs: secs,
		IndexText:    indexText,
		IsUnplayable: isUnplayable,
	}
}

func parseShowHeader(j map[string]interface{}) *ShowHeader {
	h := &ShowHeader{}
	if sidebar, ok := j["sidebar"].(map[string]interface{}); ok {
		if psr, ok := sidebar["playlistSidebarRenderer"].(map[string]interface{}); ok {
			if items, ok := psr["items"].([]interface{}); ok && len(items) > 0 {
				if im, ok := items[0].(map[string]interface{}); ok {
					if pri, ok := im["playlistSidebarPrimaryInfoRenderer"].(map[string]interface{}); ok {
						h.Title = getText(pri["title"])
						if h.Title == "" {
							if st, ok := pri["title"].(map[string]interface{}); ok {
								if s, ok := st["simpleText"].(string); ok {
									h.Title = s
								}
							}
						}
						h.Description = getText(pri["description"])
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
						if stats, ok := pri["stats"].([]interface{}); ok && len(stats) > 0 {
							if sm, ok := stats[0].(map[string]interface{}); ok {
								h.SeasonText = getText(sm)
								if h.SeasonText == "" {
									if s, ok := sm["simpleText"].(string); ok {
										h.SeasonText = s
									}
								}
							}
						}
						if overlays, ok := pri["thumbnailOverlays"].([]interface{}); ok {
							for _, ov := range overlays {
								if om, ok := ov.(map[string]interface{}); ok {
									if tvo, ok := om["tvfilmShowWatchForwardOverlayRenderer"].(map[string]interface{}); ok {
										if hdr, ok := tvo["header"].(map[string]interface{}); ok {
											h.Subtitle = getText(hdr)
										}
										if ttl, ok := tvo["title"].(map[string]interface{}); ok {
											h.OverlayTitle = getText(ttl)
										}
										if sub, ok := tvo["subtitle"].(map[string]interface{}); ok {
											h.OverlaySubtitle = getText(sub)
										}
										if prim, ok := tvo["primaryActionButton"]; ok {
											_ = prim
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
	// content section: playlistShowMetadataRenderer for seasons + episode count
	if contents, ok := j["contents"].(map[string]interface{}); ok {
		if two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{}); ok {
			if tabs, ok := two["tabs"].([]interface{}); ok && len(tabs) > 0 {
				if tm, ok := tabs[0].(map[string]interface{}); ok {
					if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
						if c, ok := tr["content"].(map[string]interface{}); ok {
							if sl, ok := c["sectionListRenderer"].(map[string]interface{}); ok {
								if arr, ok := sl["contents"].([]interface{}); ok {
									for _, sec := range arr {
										if sm, ok := sec.(map[string]interface{}); ok {
											if isr, ok := sm["itemSectionRenderer"].(map[string]interface{}); ok {
												if contents2, ok := isr["contents"].([]interface{}); ok {
													for _, item := range contents2 {
														if im, ok := item.(map[string]interface{}); ok {
															if psm, ok := im["playlistShowMetadataRenderer"].(map[string]interface{}); ok {
																if desc, ok := psm["description"]; ok {
																	txt := getText(desc)
																	if txt != "" {
																		h.EpisodeCountText = txt
																		var digits strings.Builder
																		for _, ch := range txt {
																			if ch >= '0' && ch <= '9' {
																				digits.WriteRune(ch)
																			}
																		}
																		fmt.Sscan(digits.String(), &h.EpisodeCount)
																	}
																}
																if coll, ok := psm["collection"].(map[string]interface{}); ok {
																	if sfsm, ok := coll["sortFilterSubMenuRenderer"].(map[string]interface{}); ok {
																		if sub, ok := sfsm["subMenuItems"].([]interface{}); ok {
																			for _, s := range sub {
																				if sm2, ok := s.(map[string]interface{}); ok {
																					title, _ := sm2["title"].(string)
																					sel, _ := sm2["selected"].(bool)
																					if sel {
																						h.CurrentSeason = title
																					}
																					browseId := ""
																					params := ""
																					if ne, ok := sm2["navigationEndpoint"].(map[string]interface{}); ok {
																						if be, ok := ne["browseEndpoint"].(map[string]interface{}); ok {
																							browseId, _ = be["browseId"].(string)
																							params, _ = be["params"].(string)
																						}
																					}
																					h.Seasons = append(h.Seasons, ShowSeason{Title: title, Selected: sel, BrowseID: browseId, Params: params})
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
		}
	}
	return h
}

func parseShowEpisodes(j map[string]interface{}) ([]ShowEpisode, string) {
	var episodes []ShowEpisode
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return episodes, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return episodes, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return episodes, continuation
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
		return episodes, continuation
	}
	arr, ok := secList["contents"].([]interface{})
	if !ok {
		return episodes, continuation
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
							if pvl, ok := im["playlistVideoListRenderer"].(map[string]interface{}); ok {
								if vids, ok := pvl["contents"].([]interface{}); ok {
									for _, v := range vids {
										if ep := parseShowEpisode(v, idx); ep != nil {
											episodes = append(episodes, *ep)
											idx++
										}
										if tok := extractContinuationToken(v); tok != "" {
											continuation = tok
										}
									}
								}
							} else if _, ok := im["playlistVideoRenderer"]; ok {
								if ep := parseShowEpisode(im, idx); ep != nil {
									episodes = append(episodes, *ep)
									idx++
								}
							} else if _, ok := im["playlistShowMetadataRenderer"]; ok {
								// header, skip
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
			} else {
				if tok := extractContinuationToken(sm); tok != "" {
					continuation = tok
				}
			}
		}
	}
	return episodes, continuation
}

func collectShowDetail(j map[string]interface{}) (*ShowDetailResult, error) {
	header := parseShowHeader(j)
	episodes, cont := parseShowEpisodes(j)
	playlistId := ""
	if sidebar, ok := j["sidebar"].(map[string]interface{}); ok {
		if psr, ok := sidebar["playlistSidebarRenderer"].(map[string]interface{}); ok {
			// try to get playlistId from header button watchEndpoint
			if items, ok := psr["items"].([]interface{}); ok && len(items) > 0 {
				if im, ok := items[0].(map[string]interface{}); ok {
					if pri, ok := im["playlistSidebarPrimaryInfoRenderer"].(map[string]interface{}); ok {
						if overlays, ok := pri["thumbnailOverlays"].([]interface{}); ok {
							for _, ov := range overlays {
								if om, ok := ov.(map[string]interface{}); ok {
									if tvo, ok := om["tvfilmShowWatchForwardOverlayRenderer"].(map[string]interface{}); ok {
										if btn, ok := tvo["primaryActionButton"].(map[string]interface{}); ok {
											if br, ok := btn["buttonRenderer"].(map[string]interface{}); ok {
												if cmd, ok := br["command"].(map[string]interface{}); ok {
													if we, ok := cmd["watchEndpoint"].(map[string]interface{}); ok {
														playlistId, _ = we["playlistId"].(string)
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
	if playlistId == "" {
		// fallback via contents watchEndpoint
		if contents, ok := j["contents"].(map[string]interface{}); ok {
			if two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{}); ok {
				if tabs, ok := two["tabs"].([]interface{}); ok && len(tabs) > 0 {
					if tm, ok := tabs[0].(map[string]interface{}); ok {
						if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
							if c, ok := tr["content"].(map[string]interface{}); ok {
								if sl, ok := c["sectionListRenderer"].(map[string]interface{}); ok {
									if arr, ok := sl["contents"].([]interface{}); ok {
										for _, sec := range arr {
											if sm, ok := sec.(map[string]interface{}); ok {
												if isr, ok := sm["itemSectionRenderer"].(map[string]interface{}); ok {
													if contents2, ok := isr["contents"].([]interface{}); ok {
														for _, item := range contents2 {
															if im, ok := item.(map[string]interface{}); ok {
																if pvl, ok := im["playlistVideoListRenderer"].(map[string]interface{}); ok {
																	if vids, ok := pvl["contents"].([]interface{}); ok && len(vids) > 0 {
																		if v0, ok := vids[0].(map[string]interface{}); ok {
																			if pvr, ok := v0["playlistVideoRenderer"].(map[string]interface{}); ok {
																				if ne, ok := pvr["navigationEndpoint"].(map[string]interface{}); ok {
																					if we, ok := ne["watchEndpoint"].(map[string]interface{}); ok {
																						playlistId, _ = we["playlistId"].(string)
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
			}
		}
	}
	// handle continuation via onResponseReceivedActions
	if len(episodes) == 0 {
		var all []ShowEpisode
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
									if pvl, ok := im["playlistVideoListRenderer"].(map[string]interface{}); ok {
										if vids, ok := pvl["contents"].([]interface{}); ok {
											for _, v := range vids {
												if ep := parseShowEpisode(v, len(all)); ep != nil {
													all = append(all, *ep)
												}
											}
										}
									} else if _, ok := im["playlistVideoRenderer"]; ok {
										if ep := parseShowEpisode(im, len(all)); ep != nil {
											all = append(all, *ep)
										}
									}
									if pvlWrap, ok := im["itemSectionRenderer"]; ok {
										if sem, ok := pvlWrap.(map[string]interface{}); ok {
											if contents2, ok := sem["contents"].([]interface{}); ok {
												for _, sub := range contents2 {
													if sm2, ok := sub.(map[string]interface{}); ok {
														if pvl2, ok := sm2["playlistVideoListRenderer"].(map[string]interface{}); ok {
															if vids, ok := pvl2["contents"].([]interface{}); ok {
																for _, v := range vids {
																	if ep := parseShowEpisode(v, len(all)); ep != nil {
																		all = append(all, *ep)
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
			if len(all) > 0 || contTmp != "" {
				if len(all) > 0 {
					episodes = all
				}
				cont = contTmp
			}
		}
		if eps, ok := j["onResponseReceivedEndpoints"].([]interface{}); ok && len(episodes) == 0 {
			var allE []ShowEpisode
			contE := cont
			for _, ep := range eps {
				if em, ok := ep.(map[string]interface{}); ok {
					if appendAct, ok := em["appendContinuationItemsAction"].(map[string]interface{}); ok {
						if items, ok := appendAct["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if tok := extractContinuationToken(it); tok != "" {
									contE = tok
									continue
								}
							}
						}
					}
				}
			}
			if len(allE) > 0 || contE != "" {
				if len(allE) > 0 {
					episodes = allE
				}
				if contE != "" {
					cont = contE
				}
			}
		}
	}
	if cont == "" {
		cont = extractContinuationToken(j)
	}
	return &ShowDetailResult{Header: header, Episodes: episodes, Continuation: cont, PlaylistId: playlistId}, nil
}

func FetchShow(session *InnertubeSession, playlistId string, continuation string) (*ShowDetailResult, error) {
	return FetchShowWithOption(session, playlistId, continuation, "")
}

func FetchShowWithOption(session *InnertubeSession, playlistId string, continuation string, params string) (*ShowDetailResult, error) {
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
	originalURL := "https://www.youtube.com/show/" + browseId
	if strings.Contains(browseId, "VL") {
		originalURL = "https://www.youtube.com/show/" + browseId
	} else {
		originalURL = "https://www.youtube.com/playlist?list=" + pid
	}
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
		if params != "" {
			bodyMap["params"] = params
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
	return collectShowDetail(j)
}
