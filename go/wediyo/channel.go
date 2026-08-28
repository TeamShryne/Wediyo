package wediyo

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// ---------- helpers for channel header ----------

func findPageHeaderViewModel(j map[string]interface{}) map[string]interface{} {
	if h, ok := j["header"].(map[string]interface{}); ok {
		if p, ok := h["pageHeaderRenderer"].(map[string]interface{}); ok {
			if c, ok := p["content"].(map[string]interface{}); ok {
				if vm, ok := c["pageHeaderViewModel"].(map[string]interface{}); ok {
					return vm
				}
			}
		}
		// fallback c4TabbedHeaderRenderer
		if _, ok := h["c4TabbedHeaderRenderer"]; ok {
			return nil // handled separately
		}
	}
	return nil
}

func parseChannelHeader(j map[string]interface{}) *ChannelHeader {
	vm := findPageHeaderViewModel(j)
	if vm == nil {
		return parseChannelHeaderFallback(j)
	}
	h := &ChannelHeader{}
	// channelId from metadata.channelMetadataRenderer.externalId
	if meta, ok := j["metadata"].(map[string]interface{}); ok {
		if cm, ok := meta["channelMetadataRenderer"].(map[string]interface{}); ok {
			if id, ok := cm["externalId"].(string); ok {
				h.ChannelID = id
			}
			if desc, ok := cm["description"].(string); ok {
				h.Description = desc
			}
			if u, ok := cm["channelUrl"].(string); ok {
				h.ChannelURL = u
			}
			if rss, ok := cm["rssUrl"].(string); ok {
				h.RSSUrl = rss
			}
			if kw, ok := cm["keywords"].(string); ok {
				h.Keywords = kw
			}
			// avatar fallback if needed
			if len(h.Avatars) == 0 {
				if av, ok := cm["avatar"]; ok {
					ths := parseThumbnails(av)
					if len(ths) > 0 {
						h.Avatars = ths
						h.AvatarURL = ths[len(ths)-1].URL
					}
				}
			}
		}
	}
	// title + verified
	if t, ok := vm["title"].(map[string]interface{}); ok {
		if dyn, ok := t["dynamicTextViewModel"].(map[string]interface{}); ok {
			if txt, ok := dyn["text"].(map[string]interface{}); ok {
				if c, ok := txt["content"].(string); ok {
					h.Title = c
				}
				// verified via attachmentRuns CHECK_CIRCLE_FILLED
				if runs, ok := txt["attachmentRuns"].([]interface{}); ok {
					for _, r := range runs {
						if rm, ok := r.(map[string]interface{}); ok {
							if el, ok := rm["element"].(map[string]interface{}); ok {
								if typ, ok := el["type"].(map[string]interface{}); ok {
									if imgType, ok := typ["imageType"].(map[string]interface{}); ok {
										if img, ok := imgType["image"].(map[string]interface{}); ok {
											if srcs, ok := img["sources"].([]interface{}); ok {
												for _, s := range srcs {
													if sm, ok := s.(map[string]interface{}); ok {
														if cr, ok := sm["clientResource"].(map[string]interface{}); ok {
															if name, ok := cr["imageName"].(string); ok && name == "CHECK_CIRCLE_FILLED" {
																h.Verified = true
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
	if h.Title == "" {
		// fallback from channelMetadataRenderer title
		if meta, ok := j["metadata"].(map[string]interface{}); ok {
			if cm, ok := meta["channelMetadataRenderer"].(map[string]interface{}); ok {
				if t, ok := cm["title"].(string); ok {
					h.Title = t
				}
			}
		}
	}
	// avatar
	if img, ok := vm["image"].(map[string]interface{}); ok {
		if dec, ok := img["decoratedAvatarViewModel"].(map[string]interface{}); ok {
			if av, ok := dec["avatar"].(map[string]interface{}); ok {
				if avm, ok := av["avatarViewModel"].(map[string]interface{}); ok {
					if im, ok := avm["image"].(map[string]interface{}); ok {
						ths := parseSourcesThumbnails(im)
						if len(ths) > 0 {
							h.Avatars = ths
							h.AvatarURL = ths[len(ths)-1].URL
						}
					}
				}
			}
		}
	}
	// banner
	if b, ok := vm["banner"].(map[string]interface{}); ok {
		if ib, ok := b["imageBannerViewModel"].(map[string]interface{}); ok {
			if im, ok := ib["image"].(map[string]interface{}); ok {
				ths := parseSourcesThumbnails(im)
				if len(ths) > 0 {
					h.Banners = ths
					h.BannerURL = ths[len(ths)-1].URL
				}
			}
		}
	}
	// metadata rows
	if md, ok := vm["metadata"].(map[string]interface{}); ok {
		if cm, ok := md["contentMetadataViewModel"].(map[string]interface{}); ok {
			if rows, ok := cm["metadataRows"].([]interface{}); ok {
				// row 0 handle
				if len(rows) > 0 {
					if r0, ok := rows[0].(map[string]interface{}); ok {
						if parts, ok := r0["metadataParts"].([]interface{}); ok && len(parts) > 0 {
							if p0, ok := parts[0].(map[string]interface{}); ok {
								if txt, ok := p0["text"].(map[string]interface{}); ok {
									if c, ok := txt["content"].(string); ok {
										h.Handle = c
									}
								}
							}
						}
					}
				}
				// row 1 subs + video count
				if len(rows) > 1 {
					if r1, ok := rows[1].(map[string]interface{}); ok {
						if parts, ok := r1["metadataParts"].([]interface{}); ok {
							if len(parts) > 0 {
								if p0, ok := parts[0].(map[string]interface{}); ok {
									if txt, ok := p0["text"].(map[string]interface{}); ok {
										if c, ok := txt["content"].(string); ok {
											h.SubscriberCountText = c
										}
									}
								}
							}
							if len(parts) > 1 {
								if p1, ok := parts[1].(map[string]interface{}); ok {
									if txt, ok := p1["text"].(map[string]interface{}); ok {
										if c, ok := txt["content"].(string); ok {
											h.VideoCountText = c
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
	// ensure avatar/banners normalized already via parseSourcesThumbnails -> normalizeThumbURL
	return h
}

func parseChannelHeaderFallback(j map[string]interface{}) *ChannelHeader {
	// try c4TabbedHeaderRenderer path
	if h, ok := j["header"].(map[string]interface{}); ok {
		if c4, ok := h["c4TabbedHeaderRenderer"].(map[string]interface{}); ok {
			ch := &ChannelHeader{}
			if t, ok := c4["title"].(string); ok {
				ch.Title = t
			} else {
				ch.Title = getText(c4["title"])
			}
			if id, ok := c4["channelId"].(string); ok {
				ch.ChannelID = id
			}
			if sub, ok := c4["subscriberCountText"]; ok {
				ch.SubscriberCountText = getText(sub)
			}
			// thumbnails for avatar
			if av, ok := c4["avatar"]; ok {
				ths := parseThumbnails(av)
				if len(ths) > 0 {
					ch.Avatars = ths
					ch.AvatarURL = ths[len(ths)-1].URL
				}
			}
			if banner, ok := c4["banner"]; ok {
				ths := parseThumbnails(banner)
				if len(ths) > 0 {
					ch.Banners = ths
					ch.BannerURL = ths[len(ths)-1].URL
				}
			}
			return ch
		}
	}
	// last fallback from channelMetadataRenderer only
	if meta, ok := j["metadata"].(map[string]interface{}); ok {
		if cm, ok := meta["channelMetadataRenderer"].(map[string]interface{}); ok {
			ch := &ChannelHeader{}
			if t, ok := cm["title"].(string); ok {
				ch.Title = t
			}
			if id, ok := cm["externalId"].(string); ok {
				ch.ChannelID = id
			}
			if desc, ok := cm["description"].(string); ok {
				ch.Description = desc
			}
			if u, ok := cm["channelUrl"].(string); ok {
				ch.ChannelURL = u
			}
			if av, ok := cm["avatar"]; ok {
				ths := parseThumbnails(av)
				if len(ths) > 0 {
					ch.Avatars = ths
					ch.AvatarURL = ths[len(ths)-1].URL
				}
			}
			return ch
		}
	}
	return nil
}

// ---------- tabs ----------

func parseChannelTabs(j map[string]interface{}) []ChannelTab {
	var out []ChannelTab
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return out
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return out
	}
	arr, ok := two["tabs"].([]interface{})
	if !ok {
		return out
	}
	for _, t := range arr {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if strings.TrimSpace(title) == "" {
					continue
				}
				selected, _ := tr["selected"].(bool)
				browseID := ""
				params := ""
				canonical := ""
				if ep, ok := tr["endpoint"].(map[string]interface{}); ok {
					if be, ok := ep["browseEndpoint"].(map[string]interface{}); ok {
						if id, ok := be["browseId"].(string); ok {
							browseID = id
						}
						if p, ok := be["params"].(string); ok && p != "" {
							if u, err := url.QueryUnescape(p); err == nil {
								p = u
							}
							if u2, err := url.QueryUnescape(p); err == nil && strings.Contains(p, "%") {
								p = u2
							}
							params = p
						}
						if c, ok := be["canonicalBaseUrl"].(string); ok {
							canonical = c
						}
					}
				}
				out = append(out, ChannelTab{Title: title, Selected: selected, Params: params, BrowseID: browseID, CanonicalBaseURL: canonical})
			}
		}
	}
	return out
}

// ---------- shelves / lockup video ----------

func parseChannelLockupVideo(v interface{}) *VideoMetadata {
	m, ok := v.(map[string]interface{})
	if !ok {
		return nil
	}
	contentID, _ := m["contentId"].(string)
	if contentID == "" {
		return nil
	}
	ctype, _ := m["contentType"].(string)
	if ctype != "LOCKUP_CONTENT_TYPE_VIDEO" && ctype != "" {
		// allow empty but must not be playlist etc
		if ctype != "LOCKUP_CONTENT_TYPE_VIDEO" {
			return nil
		}
	}
	title := ""
	var channelName string
	var channelID string
	var viewCountText string
	var publishedText string
	var badges []string
	var durationText string
	var thumbs []Thumbnail
	var thumbURL string
	var avatarURL string
	var avatars []Thumbnail

	// contentImage
	if ci, ok := m["contentImage"].(map[string]interface{}); ok {
		if tv, ok := ci["thumbnailViewModel"].(map[string]interface{}); ok {
			if img, ok := tv["image"].(map[string]interface{}); ok {
				ths := parseSourcesThumbnails(img)
				if len(ths) > 0 {
					thumbs = ths
					thumbURL = ths[len(ths)-1].URL
				}
			}
			// duration from overlays
			if overlays, ok := tv["overlays"].([]interface{}); ok {
				for _, ov := range overlays {
					if om, ok := ov.(map[string]interface{}); ok {
						if bot, ok := om["thumbnailBottomOverlayViewModel"].(map[string]interface{}); ok {
							if badgesArr, ok := bot["badges"].([]interface{}); ok {
								for _, b := range badgesArr {
									if bm, ok := b.(map[string]interface{}); ok {
										if tb, ok := bm["thumbnailBadgeViewModel"].(map[string]interface{}); ok {
											if txt, ok := tb["text"].(string); ok && txt != "" {
												// Heuristic: duration contains : or LIVE
												if strings.Contains(txt, ":") || strings.EqualFold(txt, "LIVE") {
													durationText = txt
												} else {
													badges = append(badges, txt)
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
	if thumbURL == "" && contentID != "" {
		thumbURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", contentID)
		thumbs = []Thumbnail{{URL: thumbURL, Width: 480, Height: 360}}
	}
	// metadata
	if meta, ok := m["metadata"].(map[string]interface{}); ok {
		if lockup, ok := meta["lockupMetadataViewModel"].(map[string]interface{}); ok {
			if t, ok := lockup["title"].(map[string]interface{}); ok {
				if c, ok := t["content"].(string); ok {
					title = c
				}
			}
			// channel avatar from image.decoratedAvatarViewModel
			if img, ok := lockup["image"].(map[string]interface{}); ok {
				if dec, ok := img["decoratedAvatarViewModel"].(map[string]interface{}); ok {
					if av, ok := dec["avatar"].(map[string]interface{}); ok {
						if avm, ok := av["avatarViewModel"].(map[string]interface{}); ok {
							if im, ok := avm["image"].(map[string]interface{}); ok {
								avs := parseSourcesThumbnails(im)
								if len(avs) > 0 {
									avatars = avs
									avatarURL = avs[len(avs)-1].URL
								}
							}
						}
					}
				}
			}
			if md, ok := lockup["metadata"].(map[string]interface{}); ok {
				if cm, ok := md["contentMetadataViewModel"].(map[string]interface{}); ok {
					if rows, ok := cm["metadataRows"].([]interface{}); ok {
						// handle both channel home (channel row + views row) and channel videos (single views row)
						// detect if first row is channel row (has browseEndpoint) vs views row (contains "views")
						isChannelRow := func(row map[string]interface{}) bool {
							if parts, ok := row["metadataParts"].([]interface{}); ok && len(parts) > 0 {
								if p0, ok := parts[0].(map[string]interface{}); ok {
									if txt, ok := p0["text"].(map[string]interface{}); ok {
										if c, ok := txt["content"].(string); ok && strings.Contains(strings.ToLower(c), "views") {
											return false
										}
										if _, hasCmd := txt["commandRuns"]; hasCmd {
											return true
										}
										// if only one part and no views keyword, treat as channel if rows==2+ else views
									}
								}
							}
							return false
						}
						rowIdx := 0
						if len(rows) > 0 {
							if r0, ok := rows[0].(map[string]interface{}); ok {
								if isChannelRow(r0) && len(rows) > 1 {
									// row 0 is channel
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
																	if id, ok := be["browseId"].(string); ok {
																		channelID = id
																	}
																}
															}
														}
													}
												}
											}
										}
									}
									rowIdx = 1
								} else {
									rowIdx = 0
								}
							}
						}
						// views + age row
						if len(rows) > rowIdx {
							if r1, ok := rows[rowIdx].(map[string]interface{}); ok {
								if parts, ok := r1["metadataParts"].([]interface{}); ok {
									if len(parts) > 0 {
										if p0, ok := parts[0].(map[string]interface{}); ok {
											if txt, ok := p0["text"].(map[string]interface{}); ok {
												if c, ok := txt["content"].(string); ok {
													viewCountText = c
												}
											}
										}
									}
									if len(parts) > 1 {
										if p1, ok := parts[1].(map[string]interface{}); ok {
											if txt, ok := p1["text"].(map[string]interface{}); ok {
												if c, ok := txt["content"].(string); ok {
													publishedText = c
												}
											}
										}
									}
								}
							}
						}
						// badges row (after views) — handle both home (channel+views+badges) and videos (views+badges)
						badgeIdx := rowIdx + 1
						if len(rows) > badgeIdx {
							if r2, ok := rows[badgeIdx].(map[string]interface{}); ok {
								if parts, ok := r2["metadataParts"].([]interface{}); ok {
									for _, p := range parts {
										if pm, ok := p.(map[string]interface{}); ok {
											if txt, ok := pm["text"].(map[string]interface{}); ok {
												if c, ok := txt["content"].(string); ok && c != "" {
													badges = append(badges, c)
												}
											}
											if b, ok := pm["badges"].([]interface{}); ok {
												for _, bv := range b {
													if bm, ok := bv.(map[string]interface{}); ok {
														if bv2, ok := bm["badgeViewModel"].(map[string]interface{}); ok {
															if t, ok := bv2["badgeText"].(string); ok && t != "" {
																badges = append(badges, t)
															}
														}
													}
												}
											}
										}
									}
								}
								// also badges field inside row
								if b, ok := r2["badges"].([]interface{}); ok {
									for _, bv := range b {
										if bm, ok := bv.(map[string]interface{}); ok {
											if bv2, ok := bm["badgeViewModel"].(map[string]interface{}); ok {
												if t, ok := bv2["badgeText"].(string); ok && t != "" {
													badges = append(badges, t)
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

	durationSecs := parseDuration(durationText)
	viewCount := parseViewCount(viewCountText)
	isLive := strings.EqualFold(durationText, "LIVE")
	for _, b := range badges {
		if strings.EqualFold(b, "LIVE") {
			isLive = true
		}
	}

	return &VideoMetadata{
		ID:                 contentID,
		Title:              title,
		Author:             channelName,
		ViewCount:          viewCount,
		ViewCountText:      viewCountText,
		PublishedTimeText:  publishedText,
		DurationText:       durationText,
		DurationSecs:       durationSecs,
		ThumbnailURL:       thumbURL,
		Thumbnails:         thumbs,
		ChannelID:          channelID,
		ChannelAvatarURL:   avatarURL,
		ChannelAvatars:     avatars,
		IsLive:             isLive,
		Badges:             badges,
		DescriptionSnippet: "",
	}
}

func parseChannelShelves(j map[string]interface{}) []ChannelShelf {
	var shelves []ChannelShelf
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return shelves
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return shelves
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return shelves
	}
	var homeContent map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				selected, _ := tr["selected"].(bool)
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if selected && strings.EqualFold(title, "Home") {
					if c, ok := tr["content"].(map[string]interface{}); ok {
						homeContent = c
						break
					}
				}
			}
		}
	}
	// fallback: first tab with selected or Home
	if homeContent == nil {
		for _, t := range tabsRaw {
			if tm, ok := t.(map[string]interface{}); ok {
				if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
					if sel, _ := tr["selected"].(bool); sel {
						if c, ok := tr["content"].(map[string]interface{}); ok {
							homeContent = c
							break
						}
					}
				}
			}
		}
	}
	if homeContent == nil {
		return shelves
	}
	secList, ok := homeContent["sectionListRenderer"].(map[string]interface{})
	if !ok {
		return shelves
	}
	arr, ok := secList["contents"].([]interface{})
	if !ok {
		return shelves
	}
	for _, sec := range arr {
		if sm, ok := sec.(map[string]interface{}); ok {
			if isr, ok := sm["itemSectionRenderer"].(map[string]interface{}); ok {
				if contents, ok := isr["contents"].([]interface{}); ok {
					for _, item := range contents {
						if im, ok := item.(map[string]interface{}); ok {
							if shelf, ok := im["shelfRenderer"].(map[string]interface{}); ok {
								title := ""
								if t, ok := shelf["title"].(map[string]interface{}); ok {
									title = getText(t)
								} else if runs, ok := shelf["title"].(map[string]interface{}); ok {
									title = getText(runs)
								}
								// alternative runs
								if title == "" {
									if tr, ok := shelf["title"].(map[string]interface{}); ok {
										if runs, ok := tr["runs"].([]interface{}); ok && len(runs) > 0 {
											if r0, ok := runs[0].(map[string]interface{}); ok {
												title, _ = r0["text"].(string)
											}
										}
									}
								}
								if title == "" {
									title = getText(shelf["title"])
								}
								browseID := ""
								if ep, ok := shelf["endpoint"].(map[string]interface{}); ok {
									if be, ok := ep["browseEndpoint"].(map[string]interface{}); ok {
										browseID, _ = be["browseId"].(string)
									}
								}
								var videos []VideoMetadata
								if content, ok := shelf["content"].(map[string]interface{}); ok {
									if hl, ok := content["horizontalListRenderer"].(map[string]interface{}); ok {
										if items, ok := hl["items"].([]interface{}); ok {
											for _, it := range items {
												if itm, ok := it.(map[string]interface{}); ok {
													if lockup, ok := itm["lockupViewModel"]; ok {
														if vm := parseChannelLockupVideo(lockup); vm != nil {
															videos = append(videos, *vm)
														}
													}
												}
											}
										}
									}
								}
								// only keep shelves with at least one video (skip channels/posts for now)
								if len(videos) > 0 {
									shelves = append(shelves, ChannelShelf{Title: title, BrowseID: browseID, Videos: videos})
								} else {
									// keep empty shelves? For home we only want video shelves, so skip empty
									// But keep title for debugging if needed? No.
								}
							}
						}
					}
				}
			}
		}
	}
	return shelves
}

func collectChannelHome(j map[string]interface{}) (*ChannelHomeResult, error) {
	header := parseChannelHeader(j)
	tabs := parseChannelTabs(j)
	shelves := parseChannelShelves(j)
	return &ChannelHomeResult{Header: header, Tabs: tabs, Shelves: shelves}, nil
}

// FetchChannelHome fetches channel home (featured) via browseId (UC... or @handle)
func FetchChannelHome(session *InnertubeSession, browseId string) (*ChannelHomeResult, error) {
	if strings.TrimSpace(browseId) == "" {
		return nil, fmt.Errorf("browseId empty")
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
	originalURL := ""
	if strings.HasPrefix(browseId, "@") {
		originalURL = "https://www.youtube.com/" + browseId
	} else if strings.HasPrefix(browseId, "UC") {
		originalURL = "https://www.youtube.com/channel/" + browseId
	} else {
		originalURL = "https://www.youtube.com/channel/" + browseId
	}
	// Try to use canonicalBaseUrl if handle known? Keep generic for now.
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
	bodyMap := map[string]interface{}{"context": context, "browseId": browseId}
	bodyBytes, _ := json.Marshal(bodyMap)
	referer := originalURL
	req, err := http.NewRequest("POST", urlStr, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Origin", "https://www.youtube.com")
	req.Header.Set("Referer", referer)
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
	return collectChannelHome(j)
}

// ---------- channel videos ----------

const channelVideosParams = "EgZ2aWRlb3PyBgQKAjoA"

func parseChannelVideoChipsFromRichGrid(header map[string]interface{}) []ChannelVideoChip {
	var out []ChannelVideoChip
	if header == nil {
		return out
	}
	if cb, ok := header["chipBarViewModel"].(map[string]interface{}); ok {
		if chips, ok := cb["chips"].([]interface{}); ok {
			for _, c := range chips {
				if cm, ok := c.(map[string]interface{}); ok {
					if vm, ok := cm["chipViewModel"].(map[string]interface{}); ok {
						text, _ := vm["text"].(string)
						if text == "" {
							text = getText(vm["text"])
						}
						if text == "" {
							continue
						}
						// Check for dropdown (Latest/Popular/Oldest in sheet)
						isDropdown := false
						if dt, ok := vm["displayType"].(string); ok && dt == "CHIP_VIEW_MODEL_DISPLAY_TYPE_DROP_DOWN" {
							isDropdown = true
						}
						// try to handle dropdown sheet
						if isDropdown {
							if tap, ok := vm["tapCommand"].(map[string]interface{}); ok {
								if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
									if sheetCmd, ok := cmd["showSheetCommand"].(map[string]interface{}); ok {
										if pls, ok := sheetCmd["panelLoadingStrategy"].(map[string]interface{}); ok {
											if ic, ok := pls["inlineContent"].(map[string]interface{}); ok {
												if svm, ok := ic["sheetViewModel"].(map[string]interface{}); ok {
													if content, ok := svm["content"].(map[string]interface{}); ok {
														if lvm, ok := content["listViewModel"].(map[string]interface{}); ok {
															if items, ok := lvm["listItems"].([]interface{}); ok {
																for _, li := range items {
																	if lim, ok := li.(map[string]interface{}); ok {
																		if lvm2, ok := lim["listItemViewModel"].(map[string]interface{}); ok {
																			title := ""
																			if t, ok := lvm2["title"].(map[string]interface{}); ok {
																				if c, ok := t["content"].(string); ok {
																					title = c
																				} else {
																					title = getText(t)
																				}
																			}
																			if title == "" {
																				continue
																			}
																			selected2, _ := lvm2["isSelected"].(bool)
																			token2 := ""
																			// token inside rendererContext.commandContext.onTap.commandExecutorCommand.commands
																			if rc, ok := lvm2["rendererContext"].(map[string]interface{}); ok {
																				if cc, ok := rc["commandContext"].(map[string]interface{}); ok {
																					if tap2, ok := cc["onTap"].(map[string]interface{}); ok {
																						if ic2, ok := tap2["innertubeCommand"].(map[string]interface{}); ok {
																							if exec, ok := ic2["commandExecutorCommand"].(map[string]interface{}); ok {
																								if cmds, ok := exec["commands"].([]interface{}); ok {
																									for _, cmd := range cmds {
																										if cm2, ok := cmd.(map[string]interface{}); ok {
																											if cc2, ok := cm2["continuationCommand"].(map[string]interface{}); ok {
																												if tok, ok := cc2["token"].(string); ok && tok != "" {
																													token2 = tok
																													break
																												}
																											}
																										}
																									}
																								}
																							} else if cc2, ok := ic2["continuationCommand"].(map[string]interface{}); ok {
																								token2, _ = cc2["token"].(string)
																							}
																						}
																					}
																				}
																			}
																			out = append(out, ChannelVideoChip{Title: title, Selected: selected2, Token: token2})
																		}
																	}
																}
																continue // skip adding outer dropdown chip itself
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
						// fallback for dropdown if sheet parsing failed -> treat as regular
						selected, _ := vm["selected"].(bool)
						token := ""
						if tap, ok := vm["tapCommand"].(map[string]interface{}); ok {
							if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
								if cc, ok := cmd["continuationCommand"].(map[string]interface{}); ok {
									token, _ = cc["token"].(string)
								}
							}
						}
						// only add outer if we didn't already expand
						if !isDropdown || token != "" {
							out = append(out, ChannelVideoChip{Title: text, Selected: selected, Token: token})
						}
					}
				}
			}
		}
	}
	return out
}

func parseChannelVideosFromRichGrid(j map[string]interface{}) ([]ChannelVideoChip, []VideoMetadata, string) {
	var chips []ChannelVideoChip
	var videos []VideoMetadata
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return chips, videos, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return chips, videos, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return chips, videos, continuation
	}
	var richGrid map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				selected, _ := tr["selected"].(bool)
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if selected && strings.EqualFold(title, "Videos") {
					if c, ok := tr["content"].(map[string]interface{}); ok {
						if rg, ok := c["richGridRenderer"].(map[string]interface{}); ok {
							richGrid = rg
							break
						}
					}
				}
			}
		}
	}
	if richGrid == nil {
		return chips, videos, continuation
	}
	if h, ok := richGrid["header"].(map[string]interface{}); ok {
		chips = parseChannelVideoChipsFromRichGrid(h)
	}
	if arr, ok := richGrid["contents"].([]interface{}); ok {
		for _, item := range arr {
			if tok := extractContinuationToken(item); tok != "" {
				continuation = tok
				continue
			}
			if im, ok := item.(map[string]interface{}); ok {
				if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
					if content, ok := ri["content"].(map[string]interface{}); ok {
						if lockup, ok := content["lockupViewModel"]; ok {
							if vm := parseChannelLockupVideo(lockup); vm != nil {
								videos = append(videos, *vm)
							}
						}
					}
				} else if lockup, ok := im["lockupViewModel"]; ok {
					if vm := parseChannelLockupVideo(lockup); vm != nil {
						videos = append(videos, *vm)
					}
				}
			}
		}
	}
	return chips, videos, continuation
}

func collectChannelVideos(j map[string]interface{}) (*ChannelVideosResult, error) {
	header := parseChannelHeader(j)
	tabs := parseChannelTabs(j)
	chips, videos, continuation := parseChannelVideosFromRichGrid(j)

	// handle continuation responses (pagination + chip reload)
	if len(chips) == 0 && len(videos) == 0 {
		var allChips []ChannelVideoChip
		var allVideos []VideoMetadata
		cont := continuation
		// onResponseReceivedActions - appendContinuationItemsAction and reloadContinuationItemsCommand
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
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if lockup, ok := content["lockupViewModel"]; ok {
												if vm := parseChannelLockupVideo(lockup); vm != nil {
													allVideos = append(allVideos, *vm)
												}
											}
										}
									} else if lockup, ok := im["lockupViewModel"]; ok {
										if vm := parseChannelLockupVideo(lockup); vm != nil {
											allVideos = append(allVideos, *vm)
										}
									}
								}
							}
						}
					}
					if reload, ok := am["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
						if items, ok := reload["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if im, ok := it.(map[string]interface{}); ok {
									// header chips reload
									if cb, ok := im["chipBarViewModel"].(map[string]interface{}); ok {
										chips2 := parseChannelVideoChipsFromRichGrid(map[string]interface{}{"chipBarViewModel": cb})
										if len(chips2) > 0 {
											allChips = chips2
										}
										continue
									}
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if lockup, ok := content["lockupViewModel"]; ok {
												if vm := parseChannelLockupVideo(lockup); vm != nil {
													allVideos = append(allVideos, *vm)
												}
											}
										}
									} else if lockup, ok := im["lockupViewModel"]; ok {
										if vm := parseChannelLockupVideo(lockup); vm != nil {
											allVideos = append(allVideos, *vm)
										}
									}
									if tok := extractContinuationToken(it); tok != "" {
										cont = tok
									}
								}
							}
						}
					}
				}
			}
			// onResponseReceivedActions continuation responses have no initial chips/videos, so override
			if len(allVideos) > 0 || len(allChips) > 0 || cont != "" {
				if len(allChips) > 0 {
					chips = allChips
				}
				if len(allVideos) > 0 {
					videos = allVideos
				}
				if cont != "" {
					continuation = cont
				}
			}
		}
		// alternative key onResponseReceivedCommands (search style) - handle for robustness
		if cmds, ok := j["onResponseReceivedCommands"].([]interface{}); ok && len(videos) == 0 {
			for _, cmd := range cmds {
				if cm, ok := cmd.(map[string]interface{}); ok {
					if appendAct, ok := cm["appendContinuationItemsAction"].(map[string]interface{}); ok {
						if items, ok := appendAct["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if tok := extractContinuationToken(it); tok != "" {
									continuation = tok
									continue
								}
								if im, ok := it.(map[string]interface{}); ok {
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if lockup, ok := content["lockupViewModel"]; ok {
												if vm := parseChannelLockupVideo(lockup); vm != nil {
													videos = append(videos, *vm)
												}
											}
										}
									}
								}
							}
						}
					}
					if reload, ok := cm["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
						if items, ok := reload["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if im, ok := it.(map[string]interface{}); ok {
									if _, ok := im["chipBarViewModel"]; ok {
										c2 := parseChannelVideoChipsFromRichGrid(map[string]interface{}{"chipBarViewModel": im["chipBarViewModel"]})
										if len(c2) > 0 {
											chips = c2
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

	// fallback continuation search generic
	if continuation == "" {
		continuation = extractContinuationToken(j)
		// re-extract via deep find token with request field
		if continuation == "" {
			var findToken func(v interface{}) string
			findToken = func(v interface{}) string {
				if m, ok := v.(map[string]interface{}); ok {
					if tok, ok := m["token"].(string); ok {
						if _, hasReq := m["request"]; hasReq {
							return tok
						}
					}
					for _, val := range m {
						if res := findToken(val); res != "" {
							return res
						}
					}
				} else if arr, ok := v.([]interface{}); ok {
					for _, el := range arr {
						if res := findToken(el); res != "" {
							return res
						}
					}
				}
				return ""
			}
			continuation = findToken(j)
		}
	}

	return &ChannelVideosResult{Header: header, Tabs: tabs, Chips: chips, Videos: videos, Continuation: continuation}, nil
}

// ---------- channel shorts ----------

const channelShortsParams = "EgZzaG9ydHPyBgUKA5oBAA=="

func parseChannelShortsFromRichGrid(j map[string]interface{}) ([]ChannelVideoChip, []ShortResult, string) {
	var chips []ChannelVideoChip
	var shorts []ShortResult
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return chips, shorts, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return chips, shorts, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return chips, shorts, continuation
	}
	var richGrid map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				selected, _ := tr["selected"].(bool)
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if selected && strings.EqualFold(title, "Shorts") {
					if c, ok := tr["content"].(map[string]interface{}); ok {
						if rg, ok := c["richGridRenderer"].(map[string]interface{}); ok {
							richGrid = rg
							break
						}
					}
				}
			}
		}
	}
	if richGrid == nil {
		return chips, shorts, continuation
	}
	if h, ok := richGrid["header"].(map[string]interface{}); ok {
		chips = parseChannelVideoChipsFromRichGrid(h)
	}
	if arr, ok := richGrid["contents"].([]interface{}); ok {
		for _, item := range arr {
			if tok := extractContinuationToken(item); tok != "" {
				continuation = tok
				continue
			}
			if im, ok := item.(map[string]interface{}); ok {
				if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
					if content, ok := ri["content"].(map[string]interface{}); ok {
						if sl, ok := content["shortsLockupViewModel"]; ok {
							if s := parseShortLockup(sl); s != nil {
								shorts = append(shorts, *s)
							}
						} else if lockup, ok := content["lockupViewModel"]; ok {
							// fallback
							if s := parseShortLockup(lockup); s != nil {
								shorts = append(shorts, *s)
							}
						}
					}
				} else if sl, ok := im["shortsLockupViewModel"]; ok {
					if s := parseShortLockup(sl); s != nil {
						shorts = append(shorts, *s)
					}
				}
			}
		}
	}
	return chips, shorts, continuation
}

func collectChannelShorts(j map[string]interface{}) (*ChannelShortsResult, error) {
	header := parseChannelHeader(j)
	tabs := parseChannelTabs(j)
	chips, shorts, continuation := parseChannelShortsFromRichGrid(j)

	if len(chips) == 0 && len(shorts) == 0 {
		var allChips []ChannelVideoChip
		var allShorts []ShortResult
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
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if sl, ok := content["shortsLockupViewModel"]; ok {
												if s := parseShortLockup(sl); s != nil {
													allShorts = append(allShorts, *s)
												}
											}
										}
									} else if sl, ok := im["shortsLockupViewModel"]; ok {
										if s := parseShortLockup(sl); s != nil {
											allShorts = append(allShorts, *s)
										}
									}
								}
							}
						}
					}
					if reload, ok := am["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
						if items, ok := reload["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if im, ok := it.(map[string]interface{}); ok {
									if cb, ok := im["chipBarViewModel"].(map[string]interface{}); ok {
										chips2 := parseChannelVideoChipsFromRichGrid(map[string]interface{}{"chipBarViewModel": cb})
										if len(chips2) > 0 {
											allChips = chips2
										}
										continue
									}
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if sl, ok := content["shortsLockupViewModel"]; ok {
												if s := parseShortLockup(sl); s != nil {
													allShorts = append(allShorts, *s)
												}
											}
										}
									}
								}
								if tok := extractContinuationToken(it); tok != "" {
									cont = tok
								}
							}
						}
					}
				}
			}
			if len(allShorts) > 0 || len(allChips) > 0 || cont != "" {
				if len(allChips) > 0 {
					chips = allChips
				}
				if len(allShorts) > 0 {
					shorts = allShorts
				}
				if cont != "" {
					continuation = cont
				}
			}
		}
		if cmds, ok := j["onResponseReceivedCommands"].([]interface{}); ok && len(shorts) == 0 && len(chips) == 0 {
			for _, cmd := range cmds {
				if cm, ok := cmd.(map[string]interface{}); ok {
					if appendAct, ok := cm["appendContinuationItemsAction"].(map[string]interface{}); ok {
						if items, ok := appendAct["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if tok := extractContinuationToken(it); tok != "" {
									continuation = tok
									continue
								}
								if im, ok := it.(map[string]interface{}); ok {
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if sl, ok := content["shortsLockupViewModel"]; ok {
												if s := parseShortLockup(sl); s != nil {
													shorts = append(shorts, *s)
												}
											}
										}
									}
								}
							}
						}
					}
					if reload, ok := cm["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
						if items, ok := reload["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if im, ok := it.(map[string]interface{}); ok {
									if _, ok := im["chipBarViewModel"]; ok {
										c2 := parseChannelVideoChipsFromRichGrid(map[string]interface{}{"chipBarViewModel": im["chipBarViewModel"]})
										if len(c2) > 0 {
											chips = c2
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
	if continuation == "" {
		continuation = extractContinuationToken(j)
		if continuation == "" {
			var findToken func(v interface{}) string
			findToken = func(v interface{}) string {
				if m, ok := v.(map[string]interface{}); ok {
					if tok, ok := m["token"].(string); ok {
						if _, hasReq := m["request"]; hasReq {
							return tok
						}
					}
					for _, val := range m {
						if res := findToken(val); res != "" {
							return res
						}
					}
				} else if arr, ok := v.([]interface{}); ok {
					for _, el := range arr {
						if res := findToken(el); res != "" {
							return res
						}
					}
				}
				return ""
			}
			continuation = findToken(j)
		}
	}
	return &ChannelShortsResult{Header: header, Tabs: tabs, Chips: chips, Shorts: shorts, Continuation: continuation}, nil
}

// FetchChannelShorts fetches channel shorts tab (grid, 3 cols). continuation paginates (chip token or next page).
func FetchChannelShorts(session *InnertubeSession, browseId string, continuation string) (*ChannelShortsResult, error) {
	if strings.TrimSpace(browseId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("browseId and continuation empty")
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
	originalURL := ""
	if strings.TrimSpace(browseId) != "" {
		if strings.HasPrefix(browseId, "@") {
			originalURL = "https://www.youtube.com/" + browseId + "/shorts"
		} else if strings.HasPrefix(browseId, "UC") {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/shorts"
		} else {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/shorts"
		}
	} else {
		originalURL = "https://www.youtube.com"
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
		bodyMap["params"] = channelShortsParams
	}
	bodyBytes, _ := json.Marshal(bodyMap)
	referer := originalURL
	req, err := http.NewRequest("POST", urlStr, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Origin", "https://www.youtube.com")
	req.Header.Set("Referer", referer)
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
	return collectChannelShorts(j)
}

// ---------- channel live (streams) ----------

const channelLiveParams = "EgdzdHJlYW1z8gYECgJ6AA=="

func parseChannelLiveFromRichGrid(j map[string]interface{}) ([]ChannelVideoChip, []VideoMetadata, string) {
	var chips []ChannelVideoChip
	var lives []VideoMetadata
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return chips, lives, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return chips, lives, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return chips, lives, continuation
	}
	var richGrid map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				selected, _ := tr["selected"].(bool)
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if selected && strings.EqualFold(title, "Live") {
					if c, ok := tr["content"].(map[string]interface{}); ok {
						if rg, ok := c["richGridRenderer"].(map[string]interface{}); ok {
							richGrid = rg
							break
						}
					}
				}
			}
		}
	}
	if richGrid == nil {
		return chips, lives, continuation
	}
	if h, ok := richGrid["header"].(map[string]interface{}); ok {
		chips = parseChannelVideoChipsFromRichGrid(h)
	}
	if arr, ok := richGrid["contents"].([]interface{}); ok {
		for _, item := range arr {
			if tok := extractContinuationToken(item); tok != "" {
				continuation = tok
				continue
			}
			if im, ok := item.(map[string]interface{}); ok {
				if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
					if content, ok := ri["content"].(map[string]interface{}); ok {
						if lockup, ok := content["lockupViewModel"]; ok {
							if vm := parseChannelLockupVideo(lockup); vm != nil {
								lives = append(lives, *vm)
							}
						}
					}
				} else if lockup, ok := im["lockupViewModel"]; ok {
					if vm := parseChannelLockupVideo(lockup); vm != nil {
						lives = append(lives, *vm)
					}
				}
			}
		}
	}
	return chips, lives, continuation
}

func collectChannelLive(j map[string]interface{}) (*ChannelLiveResult, error) {
	header := parseChannelHeader(j)
	tabs := parseChannelTabs(j)
	chips, lives, continuation := parseChannelLiveFromRichGrid(j)

	if len(chips) == 0 && len(lives) == 0 {
		var allChips []ChannelVideoChip
		var allLives []VideoMetadata
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
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if lockup, ok := content["lockupViewModel"]; ok {
												if vm := parseChannelLockupVideo(lockup); vm != nil {
													allLives = append(allLives, *vm)
												}
											}
										}
									} else if lockup, ok := im["lockupViewModel"]; ok {
										if vm := parseChannelLockupVideo(lockup); vm != nil {
											allLives = append(allLives, *vm)
										}
									}
								}
							}
						}
					}
					if reload, ok := am["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
						if items, ok := reload["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if im, ok := it.(map[string]interface{}); ok {
									if cb, ok := im["chipBarViewModel"].(map[string]interface{}); ok {
										chips2 := parseChannelVideoChipsFromRichGrid(map[string]interface{}{"chipBarViewModel": cb})
										if len(chips2) > 0 {
											allChips = chips2
										}
										continue
									}
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if lockup, ok := content["lockupViewModel"]; ok {
												if vm := parseChannelLockupVideo(lockup); vm != nil {
													allLives = append(allLives, *vm)
												}
											}
										}
									} else if lockup, ok := im["lockupViewModel"]; ok {
										if vm := parseChannelLockupVideo(lockup); vm != nil {
											allLives = append(allLives, *vm)
										}
									}
								}
								if tok := extractContinuationToken(it); tok != "" {
									cont = tok
								}
							}
						}
					}
				}
			}
			if len(allLives) > 0 || len(allChips) > 0 || cont != "" {
				if len(allChips) > 0 {
					chips = allChips
				}
				if len(allLives) > 0 {
					lives = allLives
				}
				if cont != "" {
					continuation = cont
				}
			}
		}
		if cmds, ok := j["onResponseReceivedCommands"].([]interface{}); ok && len(lives) == 0 && len(chips) == 0 {
			for _, cmd := range cmds {
				if cm, ok := cmd.(map[string]interface{}); ok {
					if appendAct, ok := cm["appendContinuationItemsAction"].(map[string]interface{}); ok {
						if items, ok := appendAct["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if tok := extractContinuationToken(it); tok != "" {
									continuation = tok
									continue
								}
								if im, ok := it.(map[string]interface{}); ok {
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if lockup, ok := content["lockupViewModel"]; ok {
												if vm := parseChannelLockupVideo(lockup); vm != nil {
													lives = append(lives, *vm)
												}
											}
										}
									}
								}
							}
						}
					}
					if reload, ok := cm["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
						if items, ok := reload["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if im, ok := it.(map[string]interface{}); ok {
									if _, ok := im["chipBarViewModel"]; ok {
										c2 := parseChannelVideoChipsFromRichGrid(map[string]interface{}{"chipBarViewModel": im["chipBarViewModel"]})
										if len(c2) > 0 {
											chips = c2
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
	if continuation == "" {
		continuation = extractContinuationToken(j)
		if continuation == "" {
			var findToken func(v interface{}) string
			findToken = func(v interface{}) string {
				if m, ok := v.(map[string]interface{}); ok {
					if tok, ok := m["token"].(string); ok {
						if _, hasReq := m["request"]; hasReq {
							return tok
						}
					}
					for _, val := range m {
						if res := findToken(val); res != "" {
							return res
						}
					}
				} else if arr, ok := v.([]interface{}); ok {
					for _, el := range arr {
						if res := findToken(el); res != "" {
							return res
						}
					}
				}
				return ""
			}
			continuation = findToken(j)
		}
	}
	return &ChannelLiveResult{Header: header, Tabs: tabs, Chips: chips, Lives: lives, Continuation: continuation}, nil
}

// FetchChannelLive fetches channel Live tab (streams). continuation paginates or chip token.
func FetchChannelLive(session *InnertubeSession, browseId string, continuation string) (*ChannelLiveResult, error) {
	if strings.TrimSpace(browseId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("browseId and continuation empty")
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
	originalURL := ""
	if strings.TrimSpace(browseId) != "" {
		if strings.HasPrefix(browseId, "@") {
			originalURL = "https://www.youtube.com/" + browseId + "/streams"
		} else if strings.HasPrefix(browseId, "UC") {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/streams"
		} else {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/streams"
		}
	} else {
		originalURL = "https://www.youtube.com"
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
		bodyMap["params"] = channelLiveParams
	}
	bodyBytes, _ := json.Marshal(bodyMap)
	referer := originalURL
	req, err := http.NewRequest("POST", urlStr, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Origin", "https://www.youtube.com")
	req.Header.Set("Referer", referer)
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
	return collectChannelLive(j)
}

// ---------- channel podcasts ----------

const channelPodcastsParams = "Eghwb2RjYXN0c_IGBQoDugEA"

func parseChannelPodcastLockup(v interface{}) *ChannelPodcast {
	m, ok := v.(map[string]interface{})
	if !ok {
		return nil
	}
	podcastID, _ := m["contentId"].(string)
	if podcastID == "" {
		return nil
	}
	if ct, _ := m["contentType"].(string); ct != "LOCKUP_CONTENT_TYPE_PODCAST" && ct != "" {
		if ct != "LOCKUP_CONTENT_TYPE_PODCAST" {
			return nil
		}
	}
	title := ""
	episodeCountText := ""
	updatedText := ""
	browseID := ""
	var thumbs []Thumbnail
	var thumbURL string
	if ci, ok := m["contentImage"].(map[string]interface{}); ok {
		if col, ok := ci["collectionThumbnailViewModel"].(map[string]interface{}); ok {
			if pt, ok := col["primaryThumbnail"].(map[string]interface{}); ok {
				if tv, ok := pt["thumbnailViewModel"].(map[string]interface{}); ok {
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
								if bw, ok := om["thumbnailOverlayBadgeViewModel"].(map[string]interface{}); ok {
									if badgesArr, ok := bw["thumbnailBadges"].([]interface{}); ok {
										for _, b := range badgesArr {
											if bm, ok := b.(map[string]interface{}); ok {
												if tb, ok := bm["thumbnailBadgeViewModel"].(map[string]interface{}); ok {
													if txt, ok := tb["text"].(string); ok && txt != "" {
														episodeCountText = txt
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
	if thumbURL == "" && podcastID != "" {
		thumbURL = fmt.Sprintf("https://i.ytimg.com/pl_c/%s/studio_square_thumbnail.jpg", podcastID)
		thumbs = []Thumbnail{{URL: thumbURL, Width: 480, Height: 480}}
	}
	if meta, ok := m["metadata"].(map[string]interface{}); ok {
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
													if strings.Contains(strings.ToLower(c), "updated") {
														updatedText = c
													} else if c == "View full podcast" {
														// ignore, but capture browseId
													}
												}
												if runs, ok := txt["commandRuns"].([]interface{}); ok && len(runs) > 0 {
													for _, rr := range runs {
														if r0, ok := rr.(map[string]interface{}); ok {
															if tap, ok := r0["onTap"].(map[string]interface{}); ok {
																if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
																	if be, ok := cmd["browseEndpoint"].(map[string]interface{}); ok {
																		if id, ok := be["browseId"].(string); ok && strings.HasPrefix(id, "VL") {
																			browseID = id
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
	// fallback browseId from VLPL?
	if browseID == "" {
		browseID = "VL" + podcastID
	}
	ec := 0
	if episodeCountText != "" {
		fmt.Sscan(strings.Fields(episodeCountText)[0], &ec)
	}
	return &ChannelPodcast{
		PodcastID:        podcastID,
		BrowseID:         browseID,
		Title:            title,
		ThumbnailURL:     thumbURL,
		Thumbnails:       thumbs,
		EpisodeCountText: episodeCountText,
		EpisodeCount:     ec,
		UpdatedText:      updatedText,
	}
}

func parseChannelPodcastsFromRichGrid(j map[string]interface{}) ([]ChannelPodcast, string) {
	var podcasts []ChannelPodcast
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return podcasts, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return podcasts, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return podcasts, continuation
	}
	var richGrid map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				selected, _ := tr["selected"].(bool)
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if selected && strings.EqualFold(title, "Podcasts") {
					if c, ok := tr["content"].(map[string]interface{}); ok {
						if rg, ok := c["richGridRenderer"].(map[string]interface{}); ok {
							richGrid = rg
							break
						}
					}
				}
			}
		}
	}
	if richGrid == nil {
		return podcasts, continuation
	}
	if arr, ok := richGrid["contents"].([]interface{}); ok {
		for _, item := range arr {
			if tok := extractContinuationToken(item); tok != "" {
				continuation = tok
				continue
			}
			if im, ok := item.(map[string]interface{}); ok {
				if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
					if content, ok := ri["content"].(map[string]interface{}); ok {
						if lockup, ok := content["lockupViewModel"]; ok {
							if p := parseChannelPodcastLockup(lockup); p != nil {
								podcasts = append(podcasts, *p)
							}
						}
					}
				} else if lockup, ok := im["lockupViewModel"]; ok {
					if p := parseChannelPodcastLockup(lockup); p != nil {
						podcasts = append(podcasts, *p)
					}
				}
			}
		}
	}
	return podcasts, continuation
}

func collectChannelPodcasts(j map[string]interface{}) (*ChannelPodcastsResult, error) {
	header := parseChannelHeader(j)
	tabs := parseChannelTabs(j)
	podcasts, continuation := parseChannelPodcastsFromRichGrid(j)

	if len(podcasts) == 0 {
		var allPodcasts []ChannelPodcast
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
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if lockup, ok := content["lockupViewModel"]; ok {
												if p := parseChannelPodcastLockup(lockup); p != nil {
													allPodcasts = append(allPodcasts, *p)
												}
											}
										}
									} else if lockup, ok := im["lockupViewModel"]; ok {
										if p := parseChannelPodcastLockup(lockup); p != nil {
											allPodcasts = append(allPodcasts, *p)
										}
									}
								}
							}
						}
					}
					if reload, ok := am["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
						if items, ok := reload["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if im, ok := it.(map[string]interface{}); ok {
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if lockup, ok := content["lockupViewModel"]; ok {
												if p := parseChannelPodcastLockup(lockup); p != nil {
													allPodcasts = append(allPodcasts, *p)
												}
											}
										}
									}
								}
								if tok := extractContinuationToken(it); tok != "" {
									cont = tok
								}
							}
						}
					}
				}
			}
			if len(allPodcasts) > 0 || cont != "" {
				if len(allPodcasts) > 0 {
					podcasts = allPodcasts
				}
				if cont != "" {
					continuation = cont
				}
			}
		}
		if cmds, ok := j["onResponseReceivedCommands"].([]interface{}); ok && len(podcasts) == 0 {
			for _, cmd := range cmds {
				if cm, ok := cmd.(map[string]interface{}); ok {
					if appendAct, ok := cm["appendContinuationItemsAction"].(map[string]interface{}); ok {
						if items, ok := appendAct["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if tok := extractContinuationToken(it); tok != "" {
									continuation = tok
									continue
								}
								if im, ok := it.(map[string]interface{}); ok {
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if lockup, ok := content["lockupViewModel"]; ok {
												if p := parseChannelPodcastLockup(lockup); p != nil {
													podcasts = append(podcasts, *p)
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
	if continuation == "" {
		continuation = extractContinuationToken(j)
	}
	return &ChannelPodcastsResult{Header: header, Tabs: tabs, Podcasts: podcasts, Continuation: continuation}, nil
}

// FetchChannelPodcasts fetches channel Podcasts tab. continuation paginates (if any).
func FetchChannelPodcasts(session *InnertubeSession, browseId string, continuation string) (*ChannelPodcastsResult, error) {
	if strings.TrimSpace(browseId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("browseId and continuation empty")
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
	originalURL := ""
	if strings.TrimSpace(browseId) != "" {
		if strings.HasPrefix(browseId, "@") {
			originalURL = "https://www.youtube.com/" + browseId + "/podcasts"
		} else if strings.HasPrefix(browseId, "UC") {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/podcasts"
		} else {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/podcasts"
		}
	} else {
		originalURL = "https://www.youtube.com"
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
		bodyMap["params"] = channelPodcastsParams
	}
	bodyBytes, _ := json.Marshal(bodyMap)
	referer := originalURL
	req, err := http.NewRequest("POST", urlStr, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Origin", "https://www.youtube.com")
	req.Header.Set("Referer", referer)
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
	return collectChannelPodcasts(j)
}

// ---------- channel playlists ----------
const channelPlaylistsParams = "EglwbGF5bGlzdHPyBgQKAkIA"

func parseChannelPlaylistLockup(v interface{}) *ChannelPlaylist {
	m, ok := v.(map[string]interface{})
	if !ok {
		return nil
	}
	playlistID, _ := m["contentId"].(string)
	if playlistID == "" {
		return nil
	}
	if ct, _ := m["contentType"].(string); ct != "" && ct != "LOCKUP_CONTENT_TYPE_PLAYLIST" && ct != "LOCKUP_CONTENT_TYPE_PODCAST" {
		return nil
	}
	title := ""
	videoCountText := ""
	browseID := ""
	var thumbs []Thumbnail
	var thumbURL string
	if ci, ok := m["contentImage"].(map[string]interface{}); ok {
		if col, ok := ci["collectionThumbnailViewModel"].(map[string]interface{}); ok {
			if pt, ok := col["primaryThumbnail"].(map[string]interface{}); ok {
				if tv, ok := pt["thumbnailViewModel"].(map[string]interface{}); ok {
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
								if bw, ok := om["thumbnailOverlayBadgeViewModel"].(map[string]interface{}); ok {
									if arr, ok := bw["thumbnailBadges"].([]interface{}); ok {
										for _, b := range arr {
											if bm, ok := b.(map[string]interface{}); ok {
												if tb, ok := bm["thumbnailBadgeViewModel"].(map[string]interface{}); ok {
													if txt, ok := tb["text"].(string); ok && txt != "" {
														videoCountText = txt
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
	if meta, ok := m["metadata"].(map[string]interface{}); ok {
		if lockup, ok := meta["lockupMetadataViewModel"].(map[string]interface{}); ok {
			if t, ok := lockup["title"].(map[string]interface{}); ok {
				if c, ok := t["content"].(string); ok {
					title = c
				} else {
					title = getText(t)
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
												// View full playlist contains browseId VL...
												if runs, ok := txt["commandRuns"].([]interface{}); ok {
													for _, rr := range runs {
														if r0, ok := rr.(map[string]interface{}); ok {
															if tap, ok := r0["onTap"].(map[string]interface{}); ok {
																if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
																	if be, ok := cmd["browseEndpoint"].(map[string]interface{}); ok {
																		if id, ok := be["browseId"].(string); ok && strings.HasPrefix(id, "VL") {
																			browseID = id
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
	if browseID == "" {
		browseID = "VL" + playlistID
	}
	vc := 0
	if videoCountText != "" {
		fmt.Sscan(strings.Fields(videoCountText)[0], &vc)
	}
	if thumbURL == "" && playlistID != "" {
		thumbURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", playlistID)
		thumbs = []Thumbnail{{URL: thumbURL, Width: 480, Height: 270}}
	}
	return &ChannelPlaylist{PlaylistID: playlistID, BrowseID: browseID, Title: title, ThumbnailURL: thumbURL, Thumbnails: thumbs, VideoCountText: videoCountText, VideoCount: vc}
}

func parseChannelPlaylistsFromSectionList(j map[string]interface{}) ([]ChannelPlaylist, string) {
	var playlists []ChannelPlaylist
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return playlists, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return playlists, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return playlists, continuation
	}
	var secList map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				selected, _ := tr["selected"].(bool)
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if selected && strings.EqualFold(title, "Playlists") {
					if c, ok := tr["content"].(map[string]interface{}); ok {
						if sl, ok := c["sectionListRenderer"].(map[string]interface{}); ok {
							secList = sl
							break
						}
					}
				}
			}
		}
	}
	if secList == nil {
		return playlists, continuation
	}
	arr, ok := secList["contents"].([]interface{})
	if !ok {
		return playlists, continuation
	}
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
							if grid, ok := im["gridRenderer"].(map[string]interface{}); ok {
								if items, ok := grid["items"].([]interface{}); ok {
									for _, it := range items {
										if tok := extractContinuationToken(it); tok != "" {
											continuation = tok
											continue
										}
										if gm, ok := it.(map[string]interface{}); ok {
											if lockup, ok := gm["lockupViewModel"]; ok {
												if p := parseChannelPlaylistLockup(lockup); p != nil {
													playlists = append(playlists, *p)
												}
											}
										}
									}
								}
								if tok := extractContinuationToken(grid); tok != "" {
									continuation = tok
								}
							}
						}
					}
				}
			} else if tok := extractContinuationToken(sm); tok != "" {
				continuation = tok
			}
		}
	}
	if continuation == "" {
		continuation = extractContinuationToken(secList)
	}
	return playlists, continuation
}

func collectChannelPlaylists(j map[string]interface{}) (*ChannelPlaylistsResult, error) {
	header := parseChannelHeader(j)
	tabs := parseChannelTabs(j)
	playlists, continuation := parseChannelPlaylistsFromSectionList(j)
	if len(playlists) == 0 {
		var all []ChannelPlaylist
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
									if grid, ok := im["gridRenderer"].(map[string]interface{}); ok {
										if gridItems, ok := grid["items"].([]interface{}); ok {
											for _, gi := range gridItems {
												if gm, ok := gi.(map[string]interface{}); ok {
													if lockup, ok := gm["lockupViewModel"]; ok {
														if p := parseChannelPlaylistLockup(lockup); p != nil {
															all = append(all, *p)
														}
													}
												}
											}
										}
									} else if lockup, ok := im["lockupViewModel"]; ok {
										if p := parseChannelPlaylistLockup(lockup); p != nil {
											all = append(all, *p)
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
					playlists = all
				}
				if cont != "" {
					continuation = cont
				}
			}
		}
	}
	if continuation == "" {
		continuation = extractContinuationToken(j)
	}
	return &ChannelPlaylistsResult{Header: header, Tabs: tabs, Playlists: playlists, Continuation: continuation}, nil
}

func FetchChannelPlaylists(session *InnertubeSession, browseId string, continuation string) (*ChannelPlaylistsResult, error) {
	if strings.TrimSpace(browseId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("browseId and continuation empty")
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
	originalURL := ""
	if strings.TrimSpace(browseId) != "" {
		if strings.HasPrefix(browseId, "@") {
			originalURL = "https://www.youtube.com/" + browseId + "/playlists"
		} else if strings.HasPrefix(browseId, "UC") {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/playlists"
		} else {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/playlists"
		}
	} else {
		originalURL = "https://www.youtube.com"
	}
	context := map[string]interface{}{
		"client": map[string]interface{}{
			"hl": "en", "gl": "IN", "remoteHost": "", "deviceMake": "", "deviceModel": "",
			"visitorData": session.VisitorData, "userAgent": userAgent + ",gzip(gfe)", "clientName": session.ClientName, "clientVersion": session.ClientVersion,
			"osName": "Windows", "osVersion": "10.0", "originalUrl": originalURL, "screenPixelDensity": 2, "platform": "DESKTOP", "clientFormFactor": "UNKNOWN_FORM_FACTOR",
			"configInfo": map[string]interface{}{}, "timeZone": tz, "browserName": "Chrome", "browserVersion": "124.0.0.0",
			"acceptHeader": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "screenWidthPoints": 1280, "screenHeightPoints": 720, "utcOffsetMinutes": 0, "userInterfaceTheme": "USER_INTERFACE_THEME_LIGHT",
		},
		"user": map[string]interface{}{"lockedSafetyMode": false},
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
		bodyMap["params"] = channelPlaylistsParams
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
	return collectChannelPlaylists(j)
}

// ---------- channel posts (polls, images, multi-images) ----------
const channelPostsParams = "EgVwb3N0c_IGBAoCSgA="

func parseChannelPost(m map[string]interface{}) *ChannelPost {
	bptr, ok := m["backstagePostThreadRenderer"].(map[string]interface{})
	if !ok {
		return nil
	}
	postWrap, ok := bptr["post"].(map[string]interface{})
	if !ok {
		return nil
	}
	pr, ok := postWrap["backstagePostRenderer"].(map[string]interface{})
	if !ok {
		return nil
	}
	postID, _ := pr["postId"].(string)
	if postID == "" {
		return nil
	}
	authorText := getText(pr["authorText"])
	// authorThumbnail multiple qualities
	var authorThumbs []Thumbnail
	var authorThumbURL string
	if av, ok := pr["authorThumbnail"]; ok {
		ths := parseThumbnails(av)
		if len(ths) > 0 {
			authorThumbs = ths
			authorThumbURL = ths[len(ths)-1].URL
		}
	}
	contentText := getText(pr["contentText"])
	published := getText(pr["publishedTimeText"])
	voteText := getText(pr["voteCount"])
	voteLabel := ""
	if v, ok := pr["voteCount"].(map[string]interface{}); ok {
		if acc, ok := v["accessibility"].(map[string]interface{}); ok {
			if ad, ok := acc["accessibilityData"].(map[string]interface{}); ok {
				voteLabel, _ = ad["label"].(string)
			}
		}
	}
	attachmentType := "none"
	var poll *ChannelPostPoll
	var images []ChannelPostImage
	var video *VideoMetadata

	if att, ok := pr["backstageAttachment"].(map[string]interface{}); ok {
		if pollRenderer, ok := att["pollRenderer"].(map[string]interface{}); ok {
			attachmentType = "poll"
			poll = &ChannelPostPoll{}
			if typ, ok := pollRenderer["type"].(string); ok {
				poll.Type = typ
			}
			if tv, ok := pollRenderer["totalVotes"].(map[string]interface{}); ok {
				poll.TotalVotesText = getText(tv)
			}
			if choices, ok := pollRenderer["choices"].([]interface{}); ok {
				for _, c := range choices {
					if cm, ok := c.(map[string]interface{}); ok {
						txt := getText(cm["text"])
						if txt != "" {
							poll.Choices = append(poll.Choices, ChannelPostPollChoice{Text: txt})
						}
					}
				}
			}
		} else if imgR, ok := att["backstageImageRenderer"].(map[string]interface{}); ok {
			attachmentType = "singleImage"
			if img, ok := imgR["image"].(map[string]interface{}); ok {
				// image.thumbnails may be single quality but handle multiple
				ths := parseThumbnails(img)
				// also check sources fallback for completeness
				if len(ths) == 0 {
					ths = parseSourcesThumbnails(img)
				}
				if len(ths) > 0 {
					images = append(images, ChannelPostImage{URL: ths[len(ths)-1].URL, Thumbnails: ths})
				} else if pick := pickThumbnail(img); pick != "" {
					images = append(images, ChannelPostImage{URL: pick, Thumbnails: []Thumbnail{{URL: pick}}})
				}
			}
		} else if multi, ok := att["postMultiImageRenderer"].(map[string]interface{}); ok {
			attachmentType = "multiImage"
			if imgs, ok := multi["images"].([]interface{}); ok {
				for _, im := range imgs {
					if imm, ok := im.(map[string]interface{}); ok {
						if bir, ok := imm["backstageImageRenderer"].(map[string]interface{}); ok {
							if img, ok := bir["image"].(map[string]interface{}); ok {
								ths := parseThumbnails(img)
								if len(ths) == 0 {
									ths = parseSourcesThumbnails(img)
								}
								if len(ths) > 0 {
									images = append(images, ChannelPostImage{URL: ths[len(ths)-1].URL, Thumbnails: ths})
								}
							}
						}
					}
				}
			}
		} else if vr, ok := att["videoRenderer"].(map[string]interface{}); ok {
			attachmentType = "video"
			// best effort: extract videoId/title/thumbs
			vid, _ := vr["videoId"].(string)
			title := getText(vr["title"])
			thumb := ""
			var ths []Thumbnail
			if thumbObj, ok := vr["thumbnail"]; ok {
				ths = parseThumbnails(thumbObj)
				if len(ths) > 0 {
					thumb = ths[len(ths)-1].URL
				}
			}
			video = &VideoMetadata{ID: vid, Title: title, ThumbnailURL: thumb, Thumbnails: ths}
		} else if vr, ok := att["backstageVideoRenderer"]; ok {
			attachmentType = "video"
			if vrm, ok := vr.(map[string]interface{}); ok {
				vid, _ := vrm["videoId"].(string)
				title := getText(vrm["title"])
				video = &VideoMetadata{ID: vid, Title: title}
			}
		}
	}
	return &ChannelPost{
		PostID: postID, AuthorText: authorText, AuthorThumbnailURL: authorThumbURL, AuthorThumbnails: authorThumbs,
		ContentText: contentText, PublishedTimeText: published, VoteCountText: voteText, VoteCount: voteLabel,
		AttachmentType: attachmentType, Poll: poll, Images: images, Video: video,
	}
}

func parseChannelPostsFromSectionList(j map[string]interface{}) ([]ChannelPost, string) {
	var posts []ChannelPost
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return posts, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return posts, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return posts, continuation
	}
	var secList map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				selected, _ := tr["selected"].(bool)
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if selected && strings.EqualFold(title, "Posts") {
					if c, ok := tr["content"].(map[string]interface{}); ok {
						if sl, ok := c["sectionListRenderer"].(map[string]interface{}); ok {
							secList = sl
							break
						}
					}
				}
			}
		}
	}
	if secList == nil {
		return posts, continuation
	}
	arr, ok := secList["contents"].([]interface{})
	if !ok {
		return posts, continuation
	}
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
							if _, hasPost := im["backstagePostThreadRenderer"]; hasPost {
								if p := parseChannelPost(im); p != nil {
									posts = append(posts, *p)
								}
							}
						}
					}
				}
			} else if tok := extractContinuationToken(sm); tok != "" {
				continuation = tok
			}
		}
	}
	if continuation == "" {
		continuation = extractContinuationToken(secList)
	}
	return posts, continuation
}

func collectChannelPosts(j map[string]interface{}) (*ChannelPostsResult, error) {
	header := parseChannelHeader(j)
	tabs := parseChannelTabs(j)
	posts, continuation := parseChannelPostsFromSectionList(j)
	if len(posts) == 0 {
		var all []ChannelPost
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
									if sec, ok := im["itemSectionRenderer"].(map[string]interface{}); ok {
										if contents2, ok := sec["contents"].([]interface{}); ok {
											for _, sub := range contents2 {
												if im2, ok := sub.(map[string]interface{}); ok {
													if _, hasPost := im2["backstagePostThreadRenderer"]; hasPost {
														if p := parseChannelPost(im2); p != nil {
															all = append(all, *p)
														}
													} else if tok := extractContinuationToken(sub); tok != "" {
														cont = tok
													}
												}
											}
										}
									} else if _, hasPost := im["backstagePostThreadRenderer"]; hasPost {
										if p := parseChannelPost(im); p != nil {
											all = append(all, *p)
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
					posts = all
				}
				if cont != "" {
					continuation = cont
				}
			}
		}
		if cmds, ok := j["onResponseReceivedCommands"].([]interface{}); ok && len(posts) == 0 {
			for _, cmd := range cmds {
				if cm, ok := cmd.(map[string]interface{}); ok {
					if appendAct, ok := cm["appendContinuationItemsAction"].(map[string]interface{}); ok {
						if items, ok := appendAct["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if tok := extractContinuationToken(it); tok != "" {
									continuation = tok
									continue
								}
								if im, ok := it.(map[string]interface{}); ok {
									if _, hasPost := im["backstagePostThreadRenderer"]; hasPost {
										if p := parseChannelPost(im); p != nil {
											posts = append(posts, *p)
										}
									}
								}
							}
						}
					}
				}
			}
		}
		// YT paginated posts via onResponseReceivedEndpoints (not Actions) — observed for PrimeTime/SET India
		if eps, ok := j["onResponseReceivedEndpoints"].([]interface{}); ok && len(posts) == 0 {
			var allE []ChannelPost
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
									if sec, ok := im["itemSectionRenderer"].(map[string]interface{}); ok {
										if contents2, ok := sec["contents"].([]interface{}); ok {
											for _, sub := range contents2 {
												if im2, ok := sub.(map[string]interface{}); ok {
													if _, hasPost := im2["backstagePostThreadRenderer"]; hasPost {
														if p := parseChannelPost(im2); p != nil {
															allE = append(allE, *p)
														}
													} else if tok := extractContinuationToken(sub); tok != "" {
														contE = tok
													}
												}
											}
										}
									} else if _, hasPost := im["backstagePostThreadRenderer"]; hasPost {
										if p := parseChannelPost(im); p != nil {
											allE = append(allE, *p)
										}
									}
								}
							}
						}
						if tgt, _ := appendAct["targetId"].(string); tgt != "" {
							// targetId is typically backstage-item-section, ignore
						}
					}
					// also append may be under reloadContinuationItemsCommand
					if reload, ok := em["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
						if items, ok := reload["continuationItems"].([]interface{}); ok {
							for _, it := range items {
								if im, ok := it.(map[string]interface{}); ok {
									if _, hasPost := im["backstagePostThreadRenderer"]; hasPost {
										if p := parseChannelPost(im); p != nil {
											allE = append(allE, *p)
										}
									}
								}
								if tok := extractContinuationToken(it); tok != "" {
									contE = tok
								}
							}
						}
					}
				}
			}
			if len(allE) > 0 || contE != "" {
				if len(allE) > 0 {
					posts = allE
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
	return &ChannelPostsResult{Header: header, Tabs: tabs, Posts: posts, Continuation: continuation}, nil
}

func FetchChannelPosts(session *InnertubeSession, browseId string, continuation string) (*ChannelPostsResult, error) {
	if strings.TrimSpace(browseId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("browseId and continuation empty")
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
	originalURL := ""
	if strings.TrimSpace(browseId) != "" {
		if strings.HasPrefix(browseId, "@") {
			originalURL = "https://www.youtube.com/" + browseId + "/posts"
		} else if strings.HasPrefix(browseId, "UC") {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/posts"
		} else {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/posts"
		}
	} else {
		originalURL = "https://www.youtube.com"
	}
	context := map[string]interface{}{
		"client": map[string]interface{}{
			"hl": "en", "gl": "IN", "remoteHost": "", "deviceMake": "", "deviceModel": "",
			"visitorData": session.VisitorData, "userAgent": userAgent + ",gzip(gfe)", "clientName": session.ClientName, "clientVersion": session.ClientVersion,
			"osName": "Windows", "osVersion": "10.0", "originalUrl": originalURL, "screenPixelDensity": 2, "platform": "DESKTOP", "clientFormFactor": "UNKNOWN_FORM_FACTOR",
			"configInfo": map[string]interface{}{}, "timeZone": tz, "browserName": "Chrome", "browserVersion": "124.0.0.0",
			"acceptHeader": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "screenWidthPoints": 1280, "screenHeightPoints": 720, "utcOffsetMinutes": 0, "userInterfaceTheme": "USER_INTERFACE_THEME_LIGHT",
		},
		"user": map[string]interface{}{"lockedSafetyMode": false},
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
		bodyMap["params"] = channelPostsParams
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
	var jj map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&jj); err != nil {
		return nil, fmt.Errorf("parse browse json: %w", err)
	}
	return collectChannelPosts(jj)
}

// ---------- channel store ----------
const channelStoreParams = "EgVzdG9yZfIGBAoCGgA="

func parseChannelStoreProduct(v interface{}) *ChannelStoreProduct {
	m, ok := v.(map[string]interface{})
	if !ok {
		return nil
	}
	vr, ok := m["verticalProductCardRenderer"].(map[string]interface{})
	if !ok {
		return nil
	}
	title, _ := vr["title"].(string)
	if title == "" {
		title = getText(vr["title"])
	}
	var thumbs []Thumbnail
	var thumbURL string
	if th, ok := vr["thumbnail"]; ok {
		ths := parseThumbnails(th)
		if len(ths) == 0 {
			ths = parseSourcesThumbnails(th)
		}
		if len(ths) > 0 {
			thumbs = ths
			thumbURL = ths[len(ths)-1].URL
		}
	}
	price := ""
	if p, ok := vr["price"].(string); ok {
		price = p
	} else {
		price = getText(vr["price"])
	}
	if price == "" {
		if deals, ok := vr["dealsData"].(map[string]interface{}); ok {
			price = getText(deals["currentPrice"])
			if price == "" {
				if cp, ok := deals["currentPrice"].(string); ok {
					price = cp
				}
			}
		}
	}
	merchant := ""
	if mn, ok := vr["merchantName"].(string); ok {
		merchant = mn
	}
	fromText := ""
	if ft, ok := vr["fromMerchantText"].(string); ok {
		fromText = ft
	} else {
		fromText = getText(vr["fromMerchantText"])
	}
	if fromText == "" {
		fromText = getText(vr["ctaText"])
	}
	productURL := ""
	if nav, ok := vr["navigationEndpoint"].(map[string]interface{}); ok {
		if cmd, ok := nav["commandExecutorCommand"].(map[string]interface{}); ok {
			if cmds, ok := cmd["commands"].([]interface{}); ok {
				for _, c := range cmds {
					if cm, ok := c.(map[string]interface{}); ok {
						if ue, ok := cm["urlEndpoint"].(map[string]interface{}); ok {
							if u, ok := ue["url"].(string); ok && u != "" {
								productURL = u
								break
							}
						}
					}
				}
			}
		} else if ue, ok := nav["urlEndpoint"].(map[string]interface{}); ok {
			productURL, _ = ue["url"].(string)
		}
	}
	if title == "" && thumbURL == "" {
		return nil
	}
	return &ChannelStoreProduct{Title: title, ThumbnailURL: thumbURL, Thumbnails: thumbs, PriceText: price, MerchantName: merchant, FromText: fromText, ProductURL: productURL}
}

func parseChannelStoreFromSectionList(j map[string]interface{}) ([]ChannelStoreProduct, string) {
	var products []ChannelStoreProduct
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return products, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return products, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return products, continuation
	}
	var secList map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				selected, _ := tr["selected"].(bool)
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if selected && strings.EqualFold(title, "Store") {
					if c, ok := tr["content"].(map[string]interface{}); ok {
						if sl, ok := c["sectionListRenderer"].(map[string]interface{}); ok {
							secList = sl
							break
						}
					}
				}
			}
		}
	}
	if secList == nil {
		return products, continuation
	}
	arr, ok := secList["contents"].([]interface{})
	if !ok {
		return products, continuation
	}
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
							if shelf, ok := im["shelfRenderer"].(map[string]interface{}); ok {
								if content, ok := shelf["content"].(map[string]interface{}); ok {
									if grid, ok := content["gridRenderer"].(map[string]interface{}); ok {
										if items, ok := grid["items"].([]interface{}); ok {
											for _, it := range items {
												if tok := extractContinuationToken(it); tok != "" {
													continuation = tok
													continue
												}
												if p := parseChannelStoreProduct(it); p != nil {
													products = append(products, *p)
												}
											}
										}
									}
								}
								if tok := extractContinuationToken(shelf); tok != "" {
									continuation = tok
								}
							}
						}
					}
				}
			} else if tok := extractContinuationToken(sm); tok != "" {
				continuation = tok
			}
		}
	}
	if continuation == "" {
		continuation = extractContinuationToken(secList)
	}
	return products, continuation
}

func collectChannelStore(j map[string]interface{}) (*ChannelStoreResult, error) {
	header := parseChannelHeader(j)
	tabs := parseChannelTabs(j)
	products, continuation := parseChannelStoreFromSectionList(j)
	if len(products) == 0 {
		var all []ChannelStoreProduct
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
								if p := parseChannelStoreProduct(it); p != nil {
									all = append(all, *p)
								}
							}
						}
					}
				}
			}
			if len(all) > 0 || cont != "" {
				if len(all) > 0 {
					products = all
				}
				if cont != "" {
					continuation = cont
				}
			}
		}
	}
	if continuation == "" {
		continuation = extractContinuationToken(j)
	}
	return &ChannelStoreResult{Header: header, Tabs: tabs, Products: products, Continuation: continuation}, nil
}

func FetchChannelStore(session *InnertubeSession, browseId string, continuation string) (*ChannelStoreResult, error) {
	if strings.TrimSpace(browseId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("browseId and continuation empty")
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
	originalURL := ""
	if strings.TrimSpace(browseId) != "" {
		if strings.HasPrefix(browseId, "@") {
			originalURL = "https://www.youtube.com/" + browseId + "/store"
		} else if strings.HasPrefix(browseId, "UC") {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/store"
		} else {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/store"
		}
	} else {
		originalURL = "https://www.youtube.com"
	}
	context := map[string]interface{}{
		"client": map[string]interface{}{
			"hl": "en", "gl": "IN", "remoteHost": "", "deviceMake": "", "deviceModel": "",
			"visitorData": session.VisitorData, "userAgent": userAgent + ",gzip(gfe)", "clientName": session.ClientName, "clientVersion": session.ClientVersion,
			"osName": "Windows", "osVersion": "10.0", "originalUrl": originalURL, "screenPixelDensity": 2, "platform": "DESKTOP", "clientFormFactor": "UNKNOWN_FORM_FACTOR",
			"configInfo": map[string]interface{}{}, "timeZone": tz, "browserName": "Chrome", "browserVersion": "124.0.0.0",
			"acceptHeader": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "screenWidthPoints": 1280, "screenHeightPoints": 720, "utcOffsetMinutes": 0, "userInterfaceTheme": "USER_INTERFACE_THEME_LIGHT",
		},
		"user": map[string]interface{}{"lockedSafetyMode": false},
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
		bodyMap["params"] = channelStoreParams
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
	var jj2 map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&jj2); err != nil {
		return nil, fmt.Errorf("parse browse json: %w", err)
	}
	return collectChannelStore(jj2)
}

// ---------- channel courses ----------
const channelCoursesParams = "Egdjb3Vyc2Vz8gYFCgPCAQA="

func parseChannelCourse(v interface{}) *ChannelCourse {
	m, ok := v.(map[string]interface{})
	if !ok {
		return nil
	}
	// playlistRenderer (not lockup)
	pr, ok := m["playlistRenderer"].(map[string]interface{})
	if ok {
		m = pr
	} else {
		// fallback directly m as playlistRenderer
		if _, hasPlaylistID := m["playlistId"]; !hasPlaylistID {
			return nil
		}
	}
	playlistID, _ := m["playlistId"].(string)
	if playlistID == "" {
		return nil
	}
	title := getText(m["title"])
	var thumbs []Thumbnail
	var thumbURL string
	if th, ok := m["thumbnails"]; ok {
		// thumbnails is [] of {thumbnails: [{url}]}
		if arr, ok := th.([]interface{}); ok && len(arr) > 0 {
			if t0, ok := arr[0].(map[string]interface{}); ok {
				ths := parseThumbnails(t0)
				if len(ths) > 0 {
					thumbs = ths
					thumbURL = ths[len(ths)-1].URL
				}
			}
		}
	}
	// also handle case where thumbnails under thumbnailRenderer
	if thumbURL == "" {
		if tr, ok := m["thumbnailRenderer"]; ok {
			if sc, ok := tr.(map[string]interface{}); ok {
				if sct, ok := sc["showCustomThumbnailRenderer"]; ok {
					if stm, ok := sct.(map[string]interface{}); ok {
						if th, ok := stm["thumbnail"]; ok {
							ths := parseThumbnails(th)
							if len(ths) > 0 {
								thumbs = ths
								thumbURL = ths[len(ths)-1].URL
							}
						}
					}
				}
			}
		}
	}
	videoCountText := ""
	vc := 0
	if vcStr, ok := m["videoCount"].(string); ok {
		videoCountText = vcStr + " videos"
		// try parse int
		fmt.Sscan(vcStr, &vc)
	} else {
		videoCountText = getText(m["videoCountText"])
		fmt.Sscan(strings.Fields(videoCountText)[0], &vc)
	}
	if videoCountText == "" && vc > 0 {
		videoCountText = fmt.Sprintf("%d videos", vc)
	}
	browseID := ""
	if ep, ok := m["navigationEndpoint"].(map[string]interface{}); ok {
		if be, ok := ep["browseEndpoint"].(map[string]interface{}); ok {
			browseID, _ = be["browseId"].(string)
		} else if we, ok := ep["watchEndpoint"].(map[string]interface{}); ok {
			if pid, ok := we["playlistId"].(string); ok && pid != "" {
				browseID = "VL" + pid
			}
		}
	}
	if viewText, ok := m["viewPlaylistText"]; ok {
		if vt, ok := viewText.(map[string]interface{}); ok {
			if runs, ok := vt["runs"].([]interface{}); ok && len(runs) > 0 {
				if r0, ok := runs[0].(map[string]interface{}); ok {
					if nav, ok := r0["navigationEndpoint"].(map[string]interface{}); ok {
						if be, ok := nav["browseEndpoint"].(map[string]interface{}); ok {
							if id2, ok := be["browseId"].(string); ok && id2 != "" {
								browseID = id2
							}
						}
					}
				}
			}
		}
	}
	if browseID == "" {
		browseID = "VL" + playlistID
	}
	if thumbURL == "" {
		thumbURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", playlistID)
	}
	return &ChannelCourse{PlaylistID: playlistID, BrowseID: browseID, Title: title, ThumbnailURL: thumbURL, Thumbnails: thumbs, VideoCountText: videoCountText, VideoCount: vc}
}

func parseChannelCoursesFromRichGrid(j map[string]interface{}) ([]ChannelCourse, string) {
	var courses []ChannelCourse
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return courses, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return courses, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return courses, continuation
	}
	var richGrid map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				selected, _ := tr["selected"].(bool)
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if selected && strings.EqualFold(title, "Courses") {
					if c, ok := tr["content"].(map[string]interface{}); ok {
						if rg, ok := c["richGridRenderer"].(map[string]interface{}); ok {
							richGrid = rg
							break
						}
					}
				}
			}
		}
	}
	if richGrid == nil {
		return courses, continuation
	}
	if arr, ok := richGrid["contents"].([]interface{}); ok {
		for _, item := range arr {
			if tok := extractContinuationToken(item); tok != "" {
				continuation = tok
				continue
			}
			if im, ok := item.(map[string]interface{}); ok {
				if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
					if content, ok := ri["content"].(map[string]interface{}); ok {
						if p := parseChannelCourse(content); p != nil {
							courses = append(courses, *p)
						} else if lockup, ok := content["lockupViewModel"]; ok {
							// fallback
							if p2 := parseChannelPlaylistLockup(lockup); p2 != nil {
								courses = append(courses, ChannelCourse{PlaylistID: p2.PlaylistID, BrowseID: p2.BrowseID, Title: p2.Title, ThumbnailURL: p2.ThumbnailURL, Thumbnails: p2.Thumbnails, VideoCountText: p2.VideoCountText, VideoCount: p2.VideoCount})
							}
						}
					}
				}
			}
		}
	}
	return courses, continuation
}

func collectChannelCourses(j map[string]interface{}) (*ChannelCoursesResult, error) {
	header := parseChannelHeader(j)
	tabs := parseChannelTabs(j)
	courses, continuation := parseChannelCoursesFromRichGrid(j)
	if len(courses) == 0 {
		var all []ChannelCourse
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
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if p := parseChannelCourse(content); p != nil {
												all = append(all, *p)
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
					courses = all
				}
				if cont != "" {
					continuation = cont
				}
			}
		}
	}
	if continuation == "" {
		continuation = extractContinuationToken(j)
	}
	return &ChannelCoursesResult{Header: header, Tabs: tabs, Courses: courses, Continuation: continuation}, nil
}

func FetchChannelCourses(session *InnertubeSession, browseId string, continuation string) (*ChannelCoursesResult, error) {
	if strings.TrimSpace(browseId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("browseId and continuation empty")
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
	originalURL := ""
	if strings.TrimSpace(browseId) != "" {
		if strings.HasPrefix(browseId, "@") {
			originalURL = "https://www.youtube.com/" + browseId + "/courses"
		} else if strings.HasPrefix(browseId, "UC") {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/courses"
		} else {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/courses"
		}
	} else {
		originalURL = "https://www.youtube.com"
	}
	context := map[string]interface{}{
		"client": map[string]interface{}{
			"hl": "en", "gl": "IN", "remoteHost": "", "deviceMake": "", "deviceModel": "",
			"visitorData": session.VisitorData, "userAgent": userAgent + ",gzip(gfe)", "clientName": session.ClientName, "clientVersion": session.ClientVersion,
			"osName": "Windows", "osVersion": "10.0", "originalUrl": originalURL, "screenPixelDensity": 2, "platform": "DESKTOP", "clientFormFactor": "UNKNOWN_FORM_FACTOR",
			"configInfo": map[string]interface{}{}, "timeZone": tz, "browserName": "Chrome", "browserVersion": "124.0.0.0",
			"acceptHeader": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "screenWidthPoints": 1280, "screenHeightPoints": 720, "utcOffsetMinutes": 0, "userInterfaceTheme": "USER_INTERFACE_THEME_LIGHT",
		},
		"user": map[string]interface{}{"lockedSafetyMode": false},
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
		bodyMap["params"] = channelCoursesParams
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
	var jj3 map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&jj3); err != nil {
		return nil, fmt.Errorf("parse browse json: %w", err)
	}
	return collectChannelCourses(jj3)
}

// ---------- channel shows ----------
const channelShowsParams = "EgVzaG93c_IGBAoCYgA="

func parseChannelShow(v interface{}) *ChannelShow {
	m, ok := v.(map[string]interface{})
	if !ok {
		return nil
	}
	gs, ok := m["gridShowRenderer"].(map[string]interface{})
	if !ok {
		return nil
	}
	title := getText(gs["title"])
	var thumbs []Thumbnail
	var thumbURL string
	if tr, ok := gs["thumbnailRenderer"].(map[string]interface{}); ok {
		if sct, ok := tr["showCustomThumbnailRenderer"].(map[string]interface{}); ok {
			if th, ok := sct["thumbnail"]; ok {
				ths := parseThumbnails(th)
				if len(ths) > 0 {
					thumbs = ths
					thumbURL = ths[len(ths)-1].URL
				}
			}
		}
	}
	browseID := ""
	showID := ""
	if ep, ok := gs["navigationEndpoint"].(map[string]interface{}); ok {
		if be, ok := ep["browseEndpoint"].(map[string]interface{}); ok {
			browseID, _ = be["browseId"].(string)
			showID = browseID
		}
	}
	subtitle := getText(gs["shortBylineText"])
	if subtitle == "" {
		subtitle = getText(gs["longBylineText"])
	}
	episodeCountText := ""
	ec := 0
	if overlays, ok := gs["thumbnailOverlays"].([]interface{}); ok {
		for _, ov := range overlays {
			if om, ok := ov.(map[string]interface{}); ok {
				if bpr, ok := om["thumbnailOverlayBottomPanelRenderer"].(map[string]interface{}); ok {
					txt := getText(bpr["text"])
					if txt != "" {
						episodeCountText = txt
						fmt.Sscan(strings.Fields(txt)[0], &ec)
					}
				}
			}
		}
	}
	if showID == "" && browseID != "" {
		showID = browseID
	}
	if thumbURL == "" && showID != "" {
		thumbURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", showID)
	}
	return &ChannelShow{ShowID: showID, BrowseID: browseID, Title: title, ThumbnailURL: thumbURL, Thumbnails: thumbs, Subtitle: subtitle, EpisodeCountText: episodeCountText, EpisodeCount: ec}
}

func parseChannelShowsFromRichGrid(j map[string]interface{}) ([]ChannelShow, string) {
	var shows []ChannelShow
	continuation := ""
	contents, ok := j["contents"].(map[string]interface{})
	if !ok {
		return shows, continuation
	}
	two, ok := contents["twoColumnBrowseResultsRenderer"].(map[string]interface{})
	if !ok {
		return shows, continuation
	}
	tabsRaw, ok := two["tabs"].([]interface{})
	if !ok {
		return shows, continuation
	}
	var richGrid map[string]interface{}
	for _, t := range tabsRaw {
		if tm, ok := t.(map[string]interface{}); ok {
			if tr, ok := tm["tabRenderer"].(map[string]interface{}); ok {
				selected, _ := tr["selected"].(bool)
				title := ""
				if s, ok := tr["title"].(string); ok {
					title = s
				} else {
					title = getText(tr["title"])
				}
				if selected && strings.EqualFold(title, "Shows") {
					if c, ok := tr["content"].(map[string]interface{}); ok {
						if rg, ok := c["richGridRenderer"].(map[string]interface{}); ok {
							richGrid = rg
							break
						}
					}
				}
			}
		}
	}
	if richGrid == nil {
		return shows, continuation
	}
	if arr, ok := richGrid["contents"].([]interface{}); ok {
		for _, item := range arr {
			if tok := extractContinuationToken(item); tok != "" {
				continuation = tok
				continue
			}
			if im, ok := item.(map[string]interface{}); ok {
				if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
					if content, ok := ri["content"].(map[string]interface{}); ok {
						if s := parseChannelShow(content); s != nil {
							shows = append(shows, *s)
						}
					}
				} else if _, ok := im["gridShowRenderer"]; ok {
					if s := parseChannelShow(im); s != nil {
						shows = append(shows, *s)
					}
				}
			}
		}
	}
	return shows, continuation
}

func collectChannelShows(j map[string]interface{}) (*ChannelShowsResult, error) {
	header := parseChannelHeader(j)
	tabs := parseChannelTabs(j)
	shows, continuation := parseChannelShowsFromRichGrid(j)
	if len(shows) == 0 {
		var all []ChannelShow
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
									if ri, ok := im["richItemRenderer"].(map[string]interface{}); ok {
										if content, ok := ri["content"].(map[string]interface{}); ok {
											if s := parseChannelShow(content); s != nil {
												all = append(all, *s)
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
					shows = all
				}
				if cont != "" {
					continuation = cont
				}
			}
		}
	}
	if continuation == "" {
		continuation = extractContinuationToken(j)
	}
	return &ChannelShowsResult{Header: header, Tabs: tabs, Shows: shows, Continuation: continuation}, nil
}

func FetchChannelShows(session *InnertubeSession, browseId string, continuation string) (*ChannelShowsResult, error) {
	if strings.TrimSpace(browseId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("browseId and continuation empty")
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
	originalURL := ""
	if strings.TrimSpace(browseId) != "" {
		if strings.HasPrefix(browseId, "@") {
			originalURL = "https://www.youtube.com/" + browseId + "/shows"
		} else if strings.HasPrefix(browseId, "UC") {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/shows"
		} else {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/shows"
		}
	} else {
		originalURL = "https://www.youtube.com"
	}
	context := map[string]interface{}{
		"client": map[string]interface{}{
			"hl": "en", "gl": "IN", "remoteHost": "", "deviceMake": "", "deviceModel": "",
			"visitorData": session.VisitorData, "userAgent": userAgent + ",gzip(gfe)", "clientName": session.ClientName, "clientVersion": session.ClientVersion,
			"osName": "Windows", "osVersion": "10.0", "originalUrl": originalURL, "screenPixelDensity": 2, "platform": "DESKTOP", "clientFormFactor": "UNKNOWN_FORM_FACTOR",
			"configInfo": map[string]interface{}{}, "timeZone": tz, "browserName": "Chrome", "browserVersion": "124.0.0.0",
			"acceptHeader": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "screenWidthPoints": 1280, "screenHeightPoints": 720, "utcOffsetMinutes": 0, "userInterfaceTheme": "USER_INTERFACE_THEME_LIGHT",
		},
		"user": map[string]interface{}{"lockedSafetyMode": false},
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
		bodyMap["params"] = channelShowsParams
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
	var jj4 map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&jj4); err != nil {
		return nil, fmt.Errorf("parse browse json: %w", err)
	}
	return collectChannelShows(jj4)
}

// FetchChannelVideos fetches channel videos tab (Latest by default). If continuation != "" it paginates or applies chip filter (continuation is chip token or next page token).
func FetchChannelVideos(session *InnertubeSession, browseId string, continuation string) (*ChannelVideosResult, error) {
	if strings.TrimSpace(browseId) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("browseId and continuation empty")
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
	originalURL := ""
	if strings.TrimSpace(browseId) != "" {
		if strings.HasPrefix(browseId, "@") {
			originalURL = "https://www.youtube.com/" + browseId + "/videos"
		} else if strings.HasPrefix(browseId, "UC") {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/videos"
		} else {
			originalURL = "https://www.youtube.com/channel/" + browseId + "/videos"
		}
	} else {
		originalURL = "https://www.youtube.com"
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
		bodyMap["params"] = channelVideosParams
	}
	bodyBytes, _ := json.Marshal(bodyMap)
	referer := originalURL
	if strings.TrimSpace(continuation) != "" && strings.TrimSpace(browseId) != "" {
		referer = originalURL
	}
	req, err := http.NewRequest("POST", urlStr, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Origin", "https://www.youtube.com")
	req.Header.Set("Referer", referer)
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
	return collectChannelVideos(j)
}

