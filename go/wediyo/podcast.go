package wediyo

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

func parsePodcastEpisode(m interface{}, idx int) *PodcastEpisode {
	lm, ok := m.(map[string]interface{})
	if !ok {
		return nil
	}
	if inner, ok := lm["lockupViewModel"].(map[string]interface{}); ok {
		lm = inner
	}
	contentId, _ := lm["contentId"].(string)
	if contentId == "" {
		// fallback try playlistVideoRenderer style
		if pvr, ok := lm["playlistVideoRenderer"].(map[string]interface{}); ok {
			vid, _ := pvr["videoId"].(string)
			title := getText(pvr["title"])
			dur := getText(pvr["lengthText"])
			secs := 0
			if s, ok := pvr["lengthSeconds"].(string); ok {
				fmt.Sscan(s, &secs)
			}
			if secs == 0 && dur != "" {
				secs = int(parseDuration(dur))
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
			return &PodcastEpisode{VideoId: vid, Title: title, ThumbnailURL: thumbURL, Thumbnails: thumbs, DurationText: dur, DurationSecs: secs}
		}
		return nil
	}
	title := ""
	duration := ""
	secs := 0
	var thumbs []Thumbnail
	thumbURL := ""
	channelName := ""
	channelId := ""
	published := ""
	viewCount := ""

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
							if badges, ok := bot["badges"].([]interface{}); ok {
								for _, b := range badges {
									if bm, ok := b.(map[string]interface{}); ok {
										if tb, ok := bm["thumbnailBadgeViewModel"].(map[string]interface{}); ok {
											if txt, ok := tb["text"].(string); ok && strings.Contains(txt, ":") {
												duration = txt
												secs = int(parseDuration(txt))
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
	if meta, ok := lm["metadata"].(map[string]interface{}); ok {
		if lockup, ok := meta["lockupMetadataViewModel"].(map[string]interface{}); ok {
			if t, ok := lockup["title"].(map[string]interface{}); ok {
				if c, ok := t["content"].(string); ok {
					title = c
				}
			}
			if md, ok := lockup["metadata"].(map[string]interface{}); ok {
				if cm, ok := md["contentMetadataViewModel"].(map[string]interface{}); ok {
					if rows, ok := cm["metadataRows"].([]interface{}); ok {
						for _, r := range rows {
							if rm, ok := r.(map[string]interface{}); ok {
								if parts, ok := rm["metadataParts"].([]interface{}); ok {
									for _, p := range parts {
										if pm, ok := p.(map[string]interface{}); ok {
											if txt, ok := pm["text"].(map[string]interface{}); ok {
												if c, ok := txt["content"].(string); ok {
													low := strings.ToLower(c)
													if strings.Contains(low, "views") {
														viewCount = c
													} else if strings.Contains(c, "ago") || strings.Contains(low, "yesterday") || strings.Contains(low, "today") {
														published = c
													}
												}
											}
											if av, ok := pm["avatarStack"].(map[string]interface{}); ok {
												if asvm, ok := av["avatarStackViewModel"].(map[string]interface{}); ok {
													if textObj, ok := asvm["text"].(map[string]interface{}); ok {
														if c, ok := textObj["content"].(string); ok {
															channelName = c
														}
														if runs, ok := textObj["commandRuns"].([]interface{}); ok && len(runs) > 0 {
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
	return &PodcastEpisode{
		VideoId: contentId, Title: title, ThumbnailURL: thumbURL, Thumbnails: thumbs,
		DurationText: duration, DurationSecs: secs, ChannelName: channelName, ChannelId: channelId,
		PublishedText: published, ViewCountText: viewCount,
	}
}

func parsePodcastHeader(j map[string]interface{}) *PodcastHeader {
	h := &PodcastHeader{}
	// Modern pageHeaderViewModel
	if hdr, ok := j["header"].(map[string]interface{}); ok {
		if pr, ok := hdr["pageHeaderRenderer"].(map[string]interface{}); ok {
			if c, ok := pr["content"].(map[string]interface{}); ok {
				if vm, ok := c["pageHeaderViewModel"].(map[string]interface{}); ok {
					if t, ok := vm["title"].(map[string]interface{}); ok {
						if dyn, ok := t["dynamicTextViewModel"].(map[string]interface{}); ok {
							if txt, ok := dyn["text"].(map[string]interface{}); ok {
								h.Title, _ = txt["content"].(string)
							}
						}
					}
					if hero, ok := vm["heroImage"].(map[string]interface{}); ok {
						if cp, ok := hero["contentPreviewImageViewModel"].(map[string]interface{}); ok {
							if img, ok := cp["image"].(map[string]interface{}); ok {
								ths := parseSourcesThumbnails(img)
								if len(ths) > 0 {
									h.Thumbnails = ths
									h.ThumbnailURL = ths[len(ths)-1].URL
								}
							}
						}
					}
					if bg, ok := vm["background"].(map[string]interface{}); ok {
						if ccvm, ok := bg["cinematicContainerViewModel"].(map[string]interface{}); ok {
							if bic, ok := ccvm["backgroundImageConfig"].(map[string]interface{}); ok {
								if img, ok := bic["image"].(map[string]interface{}); ok {
									ths := parseSourcesThumbnails(img)
									if len(ths) > 0 && len(h.Thumbnails) == 0 {
										h.Thumbnails = ths
										h.ThumbnailURL = ths[len(ths)-1].URL
									}
								}
							}
						}
					}
					if md, ok := vm["metadata"].(map[string]interface{}); ok {
						if cm, ok := md["contentMetadataViewModel"].(map[string]interface{}); ok {
							if rows, ok := cm["metadataRows"].([]interface{}); ok {
								for _, r := range rows {
									if rm, ok := r.(map[string]interface{}); ok {
										if parts, ok := rm["metadataParts"].([]interface{}); ok {
											for _, p := range parts {
												if pm, ok := p.(map[string]interface{}); ok {
													if txt, ok := pm["text"].(map[string]interface{}); ok {
														if c, ok := txt["content"].(string); ok {
															low := strings.ToLower(c)
															if low == "podcast" {
																continue
															} else if strings.Contains(c, "episodes") {
																h.EpisodeCountText = c
																var digits strings.Builder
																for _, ch := range c {
																	if ch >= '0' && ch <= '9' {
																		digits.WriteRune(ch)
																	}
																}
																fmt.Sscan(digits.String(), &h.EpisodeCount)
															} else if strings.Contains(low, "updated") || strings.Contains(low, "yesterday") || strings.Contains(low, "today") {
																h.UpdatedText = c
															}
														}
													}
													if av, ok := pm["avatarStack"].(map[string]interface{}); ok {
														if asvm, ok := av["avatarStackViewModel"].(map[string]interface{}); ok {
															if textObj, ok := asvm["text"].(map[string]interface{}); ok {
																if c, ok := textObj["content"].(string); ok {
																	h.ChannelName = c
																}
																if runs, ok := textObj["commandRuns"].([]interface{}); ok && len(runs) > 0 {
																	if r0, ok := runs[0].(map[string]interface{}); ok {
																		if tap, ok := r0["onTap"].(map[string]interface{}); ok {
																			if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
																				if be, ok := cmd["browseEndpoint"].(map[string]interface{}); ok {
																					h.ChannelId, _ = be["browseId"].(string)
																					h.ChannelHandle, _ = be["canonicalBaseUrl"].(string)
																				}
																			}
																		}
																	}
																}
															}
															if avatars, ok := asvm["avatars"].([]interface{}); ok && len(avatars) > 0 {
																if av0, ok := avatars[0].(map[string]interface{}); ok {
																	if avm, ok := av0["avatarViewModel"].(map[string]interface{}); ok {
																		if img, ok := avm["image"].(map[string]interface{}); ok {
																			ths := parseSourcesThumbnails(img)
																			if len(ths) > 0 {
																				h.ChannelAvatars = ths
																				h.ChannelAvatarURL = ths[len(ths)-1].URL
																			} else {
																				ths := parseThumbnails(img)
																				if len(ths) > 0 {
																					h.ChannelAvatars = ths
																					h.ChannelAvatarURL = ths[len(ths)-1].URL
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
					if desc, ok := vm["description"].(map[string]interface{}); ok {
						if dp, ok := desc["descriptionPreviewViewModel"].(map[string]interface{}); ok {
							_ = dp
						}
					}
				}
			}
		}
	}
	// fallback sidebar
	if h.Title == "" {
		if sidebar, ok := j["sidebar"].(map[string]interface{}); ok {
			if psr, ok := sidebar["playlistSidebarRenderer"].(map[string]interface{}); ok {
				if items, ok := psr["items"].([]interface{}); ok && len(items) > 0 {
					if im, ok := items[0].(map[string]interface{}); ok {
						if pri, ok := im["playlistSidebarPrimaryInfoRenderer"].(map[string]interface{}); ok {
							if t := getText(pri["title"]); t != "" {
								h.Title = t
							}
							if d := getText(pri["description"]); d != "" {
								h.Description = d
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
								} else if pt, ok := thr["playlistCustomThumbnailRenderer"].(map[string]interface{}); ok {
									if th, ok := pt["thumbnail"]; ok {
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
										if strings.Contains(txt, "episodes") {
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
								}
							}
						}
					}
				}
			}
		}
	}
	// microformat description fallback
	if h.Description == "" {
		if mf, ok := j["microformat"].(map[string]interface{}); ok {
			if pmr, ok := mf["microformatDataRenderer"].(map[string]interface{}); ok {
				if d, ok := pmr["description"].(string); ok {
					h.Description = d
				}
			}
		}
	}
	return h
}

func parsePodcastEpisodes(j map[string]interface{}) ([]PodcastEpisode, string) {
	var episodes []PodcastEpisode
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
							if _, ok := im["lockupViewModel"]; ok {
								if ep := parsePodcastEpisode(im, idx); ep != nil {
									episodes = append(episodes, *ep)
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
			} else {
				if tok := extractContinuationToken(sm); tok != "" {
					if continuation == "" {
						continuation = tok
					}
				}
			}
		}
	}
	return episodes, continuation
}

func collectPodcastDetail(j map[string]interface{}) (*PodcastDetailResult, error) {
	header := parsePodcastHeader(j)
	episodes, cont := parsePodcastEpisodes(j)
	playlistId := ""
	if sidebar, ok := j["sidebar"].(map[string]interface{}); ok {
		if psr, ok := sidebar["playlistSidebarRenderer"].(map[string]interface{}); ok {
			if items, ok := psr["items"].([]interface{}); ok && len(items) > 0 {
				if im, ok := items[0].(map[string]interface{}); ok {
					if pri, ok := im["playlistSidebarPrimaryInfoRenderer"].(map[string]interface{}); ok {
						if menu, ok := pri["menu"]; ok {
							_ = menu
						}
					}
				}
			}
		}
	}
	// Try to get playlistId from header navigation or microformat
	if playlistId == "" {
		if mf, ok := j["microformat"].(map[string]interface{}); ok {
			if pmr, ok := mf["microformatDataRenderer"].(map[string]interface{}); ok {
				if u, ok := pmr["urlCanonical"].(string); ok {
					if idx := strings.Index(u, "list="); idx != -1 {
						playlistId = u[idx+5:]
						if amp := strings.Index(playlistId, "&"); amp != -1 {
							playlistId = playlistId[:amp]
						}
					}
				}
			}
		}
	}
	if playlistId == "" && len(episodes) > 0 {
		// fallback: try first episode's watchEndpoint playlistId is known? Use browseId from request
		// will be filled by caller via browseId
	}
	// handle continuation via onResponseReceivedActions
	if len(episodes) == 0 {
		var all []PodcastEpisode
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
									if pvlWrap, ok := im["itemSectionRenderer"]; ok {
										if sem, ok := pvlWrap.(map[string]interface{}); ok {
											if contents2, ok := sem["contents"].([]interface{}); ok {
												for _, sub := range contents2 {
													if ep := parsePodcastEpisode(sub, len(all)); ep != nil {
														all = append(all, *ep)
													}
													if tok := extractContinuationToken(sub); tok != "" {
														contTmp = tok
													}
												}
											}
										}
									} else if _, ok := im["lockupViewModel"]; ok {
										if ep := parsePodcastEpisode(im, len(all)); ep != nil {
											all = append(all, *ep)
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
	}
	if cont == "" {
		cont = extractContinuationToken(j)
	}
	// also check for continuationItemViewModel at sectionList level
	if cont == "" {
		if contents, ok := j["contents"].(map[string]interface{}); ok {
			if two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{}); ok {
				if tabs, ok := two["tabs"].([]interface{}); ok && len(tabs) > 0 {
					if tm, ok := tabs[0].(map[string]interface{}); ok {
						if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
							if c, ok := tr["content"].(map[string]interface{}); ok {
								if sl, ok := c["sectionListRenderer"].(map[string]interface{}); ok {
									if arr, ok := sl["contents"].([]interface{}); ok {
										for _, sec := range arr {
											if tok := extractContinuationToken(sec); tok != "" {
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
	return &PodcastDetailResult{Header: header, Episodes: episodes, Continuation: cont, PlaylistId: playlistId}, nil
}

func FetchPodcast(session *InnertubeSession, playlistId string, continuation string) (*PodcastDetailResult, error) {
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
	res, err := collectPodcastDetail(j)
	if err != nil {
		return nil, err
	}
	if res.PlaylistId == "" {
		res.PlaylistId = pid
	}
	return res, nil
}
