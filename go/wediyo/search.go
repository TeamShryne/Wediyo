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

// helpers for generic JSON (map[string]interface{})
func asMap(v interface{}) map[string]interface{} {
	if m, ok := v.(map[string]interface{}); ok {
		return m
	}
	return nil
}

func getText(v interface{}) string {
	m := asMap(v)
	if m == nil {
		return ""
	}
	if s, ok := m["simpleText"].(string); ok && s != "" {
		return s
	}
	if runs, ok := m["runs"].([]interface{}); ok {
		var b strings.Builder
		for _, r := range runs {
			if rm, ok := r.(map[string]interface{}); ok {
				if t, ok := rm["text"].(string); ok {
					b.WriteString(t)
				}
			}
		}
		return b.String()
	}
	return ""
}

func pickThumbnail(v interface{}) string {
	thumbs := parseThumbnails(v)
	if len(thumbs) == 0 {
		return ""
	}
	return thumbs[len(thumbs)-1].URL
}

func parseThumbnails(v interface{}) []Thumbnail {
	m := asMap(v)
	if m == nil {
		return nil
	}
	arr, ok := m["thumbnails"].([]interface{})
	if !ok {
		return nil
	}
	var out []Thumbnail
	for _, e := range arr {
		if em, ok := e.(map[string]interface{}); ok {
			u, _ := em["url"].(string)
			if u == "" {
				continue
			}
			w, _ := em["width"].(float64)
			h, _ := em["height"].(float64)
			out = append(out, Thumbnail{URL: u, Width: int(w), Height: int(h)})
		}
	}
	return out
}

func parseSourcesThumbnails(v interface{}) []Thumbnail {
	// for contentPreviewImageViewModel.image.sources where url/width/height
	m := asMap(v)
	if m == nil {
		return nil
	}
	// v may be image object with sources
	var arr []interface{}
	if srcs, ok := m["sources"].([]interface{}); ok {
		arr = srcs
	} else {
		return nil
	}
	var out []Thumbnail
	for _, e := range arr {
		if em, ok := e.(map[string]interface{}); ok {
			u, _ := em["url"].(string)
			if u == "" {
				continue
			}
			w, _ := em["width"].(float64)
			h, _ := em["height"].(float64)
			out = append(out, Thumbnail{URL: u, Width: int(w), Height: int(h)})
		}
	}
	return out
}

func parseViewCount(text string) int64 {
	var digits strings.Builder
	for _, c := range text {
		if c >= '0' && c <= '9' {
			digits.WriteRune(c)
		}
	}
	s := digits.String()
	if s == "" {
		return 0
	}
	var n int64
	fmt.Sscan(s, &n)
	return n
}

func parseDuration(text string) int64 {
	if strings.EqualFold(text, "LIVE") {
		return 0
	}
	parts := strings.Split(text, ":")
	switch len(parts) {
	case 3:
		var h, m, s int64
		fmt.Sscan(strings.ReplaceAll(parts[0], ",", ""), &h)
		fmt.Sscan(parts[1], &m)
		fmt.Sscan(parts[2], &s)
		return h*3600 + m*60 + s
	case 2:
		var m, s int64
		fmt.Sscan(strings.ReplaceAll(parts[0], ",", ""), &m)
		fmt.Sscan(parts[1], &s)
		return m*60 + s
	case 1:
		var n int64
		fmt.Sscan(strings.ReplaceAll(parts[0], ",", ""), &n)
		return n
	default:
		return 0
	}
}

func extractChannelID(vr map[string]interface{}) string {
	for _, key := range []string{"longBylineText", "ownerText", "shortBylineText"} {
		if v, ok := vr[key]; ok {
			if m := asMap(v); m != nil {
				if runs, ok := m["runs"].([]interface{}); ok && len(runs) > 0 {
					if rm, ok := runs[0].(map[string]interface{}); ok {
						if ne, ok := rm["navigationEndpoint"].(map[string]interface{}); ok {
							if be, ok := ne["browseEndpoint"].(map[string]interface{}); ok {
								if id, ok := be["browseId"].(string); ok && id != "" {
									return id
								}
							}
						}
					}
				}
			}
		}
	}
	return ""
}
func extractChannelName(vr map[string]interface{}) string {
	for _, key := range []string{"longBylineText", "ownerText", "shortBylineText"} {
		if v, ok := vr[key]; ok {
			if t := getText(v); t != "" {
				return t
			}
		}
	}
	return "Unknown"
}

func parseVideoRenderer(v interface{}) *VideoMetadata {
	m := asMap(v)
	if m == nil {
		return nil
	}
	id, _ := m["videoId"].(string)
	if id == "" {
		return nil
	}
	title := getText(m["title"])
	author := extractChannelName(m)
	channelID := extractChannelID(m)

	viewCountText := getText(m["viewCountText"])
	shortViewCountText := getText(m["shortViewCountText"])
	publishedTimeText := getText(m["publishedTimeText"])
	durationText := getText(m["lengthText"])
	if durationText == "" {
		if arr, ok := m["thumbnailOverlays"].([]interface{}); ok {
			for _, o := range arr {
				if om, ok := o.(map[string]interface{}); ok {
					if r, ok := om["thumbnailOverlayTimeStatusRenderer"].(map[string]interface{}); ok {
						if t := getText(r["text"]); t != "" {
							durationText = t
							break
						}
					}
				}
			}
		}
	}
	durationSecs := parseDuration(durationText)
	var viewCount int64
	if viewCountText != "" {
		viewCount = parseViewCount(viewCountText)
	} else if shortViewCountText != "" {
		viewCount = parseViewCount(shortViewCountText)
	}
	thumbs := parseThumbnails(m["thumbnail"])
	thumbURL := ""
	if len(thumbs) > 0 {
		thumbURL = thumbs[len(thumbs)-1].URL
	}
	if thumbURL == "" {
		thumbURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", id)
		thumbs = []Thumbnail{{URL: thumbURL, Width: 480, Height: 360}}
	}
	avatarURL := ""
	var avatars []Thumbnail
	if c, ok := m["channelThumbnailSupportedRenderers"]; ok {
		if cm, ok := c.(map[string]interface{}); ok {
			if link, ok := cm["channelThumbnailWithLinkRenderer"]; ok {
				if lm, ok := link.(map[string]interface{}); ok {
					avatarURL = pickThumbnail(lm["thumbnail"])
					avatars = parseThumbnails(lm["thumbnail"])
				}
			}
		}
	}
	isLive := false
	var badges []string
	if arr, ok := m["badges"].([]interface{}); ok {
		for _, b := range arr {
			if bm, ok := b.(map[string]interface{}); ok {
				if r, ok := bm["metadataBadgeRenderer"].(map[string]interface{}); ok {
					if label, ok := r["label"].(string); ok && label != "" {
						badges = append(badges, label)
						if strings.EqualFold(label, "LIVE") {
							isLive = true
						}
					} else if style, ok := r["style"].(string); ok && style != "" {
						badges = append(badges, style)
					}
					if icon, ok := r["icon"].(map[string]interface{}); ok {
						if it, ok := icon["iconType"].(string); ok && it == "LIVE" {
							isLive = true
						}
					}
				}
			}
		}
	}
	if strings.EqualFold(durationText, "LIVE") {
		isLive = true
	}
	snippet := ""
	if arr, ok := m["detailedMetadataSnippets"].([]interface{}); ok && len(arr) > 0 {
		if sm, ok := arr[0].(map[string]interface{}); ok {
			snippet = getText(sm["snippetText"])
		}
	}
	return &VideoMetadata{
		ID:                 id,
		Title:              title,
		Author:             author,
		ViewCount:          viewCount,
		ViewCountText:      viewCountText,
		ShortViewCountText: shortViewCountText,
		PublishedTimeText:  publishedTimeText,
		DurationText:       durationText,
		DurationSecs:       durationSecs,
		ThumbnailURL:       thumbURL,
		Thumbnails:         thumbs,
		ChannelID:          channelID,
		ChannelAvatarURL:   avatarURL,
		ChannelAvatars:     avatars,
		IsLive:             isLive,
		Badges:             badges,
		DescriptionSnippet: snippet,
	}
}

func parseChannelRenderer(v interface{}) *ChannelResult {
	m := asMap(v)
	if m == nil {
		return nil
	}
	var channelID string
	if id, ok := m["channelId"].(string); ok && id != "" {
		channelID = id
	} else if ne, ok := m["navigationEndpoint"].(map[string]interface{}); ok {
		if be, ok := ne["browseEndpoint"].(map[string]interface{}); ok {
			channelID, _ = be["browseId"].(string)
		}
	}
	if channelID == "" {
		return nil
	}
	title := getText(m["title"])
	if title == "" {
		title = getText(m["shortBylineText"])
	}
	handleStr := getText(m["subscriberCountText"])
	handle := ""
	if strings.HasPrefix(handleStr, "@") {
		handle = handleStr
	}
	subscriberText := getText(m["videoCountText"])
	if subscriberText == "" {
		subscriberText = getText(m["subscriberCountText"])
		if strings.HasPrefix(subscriberText, "@") {
			subscriberText = ""
		}
	}
	thumbs := parseThumbnails(m["thumbnail"])
	thumb := ""
	if len(thumbs) > 0 {
		thumb = thumbs[len(thumbs)-1].URL
	}
	desc := getText(m["descriptionSnippet"])
	verified := false
	var badges []string
	var badgeSources [][]interface{}
	if arr, ok := m["ownerBadges"].([]interface{}); ok {
		badgeSources = append(badgeSources, arr)
	}
	if arr, ok := m["badges"].([]interface{}); ok {
		badgeSources = append(badgeSources, arr)
	}
	for _, arr := range badgeSources {
		for _, b := range arr {
			if bm, ok := b.(map[string]interface{}); ok {
				if r, ok := bm["metadataBadgeRenderer"].(map[string]interface{}); ok {
					if style, ok := r["style"].(string); ok {
						badges = append(badges, style)
						if strings.Contains(style, "VERIFIED") {
							verified = true
						}
					}
					if icon, ok := r["icon"].(map[string]interface{}); ok {
						if it, ok := icon["iconType"].(string); ok && strings.Contains(it, "CHECK") {
							verified = true
						}
					}
				}
			}
		}
	}
	return &ChannelResult{
		ChannelID:           channelID,
		Title:               title,
		Handle:              handle,
		SubscriberCountText: subscriberText,
		ThumbnailURL:        thumb,
		Thumbnails:          thumbs,
		DescriptionSnippet:  desc,
		Verified:            verified,
		Badges:              badges,
	}
}

func parseShortLockup(v interface{}) *ShortResult {
	m := asMap(v)
	if m == nil {
		return nil
	}
	var videoID string
	if tap, ok := m["onTap"].(map[string]interface{}); ok {
		if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
			if re, ok := cmd["reelWatchEndpoint"].(map[string]interface{}); ok {
				videoID, _ = re["videoId"].(string)
			}
		}
	}
	if videoID == "" {
		if eid, ok := m["entityId"].(string); ok {
			videoID = strings.TrimPrefix(eid, "shorts-shelf-item-")
		}
	}
	if videoID == "" {
		if id, ok := m["videoId"].(string); ok {
			videoID = id
		}
	}
	if videoID == "" {
		return nil
	}
	acc, _ := m["accessibilityText"].(string)
	title := ""
	if acc != "" {
		title = strings.Split(acc, " - play Short")[0]
	}
	var thumb string
	var thumbs []Thumbnail
	if tap, ok := m["onTap"].(map[string]interface{}); ok {
		if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
			if re, ok := cmd["reelWatchEndpoint"].(map[string]interface{}); ok {
				thumbs = parseThumbnails(re["thumbnail"])
				if len(thumbs) > 0 {
					thumb = thumbs[len(thumbs)-1].URL
				}
			}
		}
	}
	if len(thumbs) == 0 {
		thumbs = parseThumbnails(m["thumbnail"])
		if len(thumbs) > 0 && thumb == "" {
			thumb = thumbs[len(thumbs)-1].URL
		}
	}
	viewText := ""
	if acc != "" {
		if idx := strings.LastIndex(acc, ","); idx != -1 {
			tail := strings.TrimSpace(acc[idx+1:])
			if strings.Contains(tail, "views") {
				viewText = strings.Split(tail, " - ")[0]
			}
		}
	}
	return &ShortResult{
		VideoID:            videoID,
		Title:              title,
		ThumbnailURL:       thumb,
		Thumbnails:         thumbs,
		ViewCountText:      viewText,
		AccessibilityLabel: acc,
	}
}

func parsePlaylistLockup(v interface{}) *PlaylistResult {
	m := asMap(v)
	if m == nil {
		return nil
	}
	ct, _ := m["contentType"].(string)
	if ct != "LOCKUP_CONTENT_TYPE_PLAYLIST" && ct != "LOCKUP_CONTENT_TYPE_COURSE" {
		// also handle course as playlist
		if ct != "" && ct != "LOCKUP_CONTENT_TYPE_PLAYLIST" {
			return nil
		}
	}
	playlistID, _ := m["contentId"].(string)
	if playlistID == "" {
		return nil
	}
	title := ""
	if meta, ok := m["metadata"].(map[string]interface{}); ok {
		if lockup, ok := meta["lockupMetadataViewModel"].(map[string]interface{}); ok {
			if t, ok := lockup["title"].(map[string]interface{}); ok {
				title, _ = t["content"].(string)
			}
		}
	}
	channelName := ""
	channelID := ""
	if meta, ok := m["metadata"].(map[string]interface{}); ok {
		if lockup, ok := meta["lockupMetadataViewModel"].(map[string]interface{}); ok {
			if md, ok := lockup["metadata"].(map[string]interface{}); ok {
				if cm, ok := md["contentMetadataViewModel"].(map[string]interface{}); ok {
					if rows, ok := cm["metadataRows"].([]interface{}); ok && len(rows) > 0 {
						if first, ok := rows[0].(map[string]interface{}); ok {
							if parts, ok := first["metadataParts"].([]interface{}); ok && len(parts) > 0 {
								if p0, ok := parts[0].(map[string]interface{}); ok {
									if txt, ok := p0["text"].(map[string]interface{}); ok {
										channelName, _ = txt["content"].(string)
										if runs, ok := txt["commandRuns"].([]interface{}); ok && len(runs) > 0 {
											if r0, ok := runs[0].(map[string]interface{}); ok {
												if tap, ok := r0["onTap"].(map[string]interface{}); ok {
													if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
														if be, ok := cmd["browseEndpoint"].(map[string]interface{}); ok {
															channelID, _ = be["browseId"].(string)
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
	var thumbs []Thumbnail
	var thumbURL string
	var videoCountText string
	isCourse := false
	var badges []string
	if ci, ok := m["contentImage"].(map[string]interface{}); ok {
		if col, ok := ci["collectionThumbnailViewModel"].(map[string]interface{}); ok {
			if pt, ok := col["primaryThumbnail"].(map[string]interface{}); ok {
				if tv, ok := pt["thumbnailViewModel"].(map[string]interface{}); ok {
					if img, ok := tv["image"].(map[string]interface{}); ok {
						thumbs = parseSourcesThumbnails(img)
						if len(thumbs) > 0 {
							thumbURL = thumbs[len(thumbs)-1].URL
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
														videoCountText = txt
														badges = append(badges, txt)
													}
													if ic, ok := tb["icon"].(map[string]interface{}); ok {
														if srcs, ok := ic["sources"].([]interface{}); ok && len(srcs) > 0 {
															if s0, ok := srcs[0].(map[string]interface{}); ok {
																if cr, ok := s0["clientResource"].(map[string]interface{}); ok {
																	if name, ok := cr["imageName"].(string); ok {
																		badges = append(badges, name)
																		if name == "COURSE" {
																			isCourse = true
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
	videoCount := 0
	if videoCountText != "" {
		fmt.Sscan(strings.Fields(videoCountText)[0], &videoCount)
	}
	// description snippet is often next metadata row with first video title
	desc := ""
	if meta, ok := m["metadata"].(map[string]interface{}); ok {
		if lockup, ok := meta["lockupMetadataViewModel"].(map[string]interface{}); ok {
			if md, ok := lockup["metadata"].(map[string]interface{}); ok {
				if cm, ok := md["contentMetadataViewModel"].(map[string]interface{}); ok {
					if rows, ok := cm["metadataRows"].([]interface{}); ok && len(rows) >= 3 {
						if third, ok := rows[2].(map[string]interface{}); ok {
							if parts, ok := third["metadataParts"].([]interface{}); ok && len(parts) > 0 {
								if p0, ok := parts[0].(map[string]interface{}); ok {
									if txt, ok := p0["text"].(map[string]interface{}); ok {
										desc, _ = txt["content"].(string)
									}
								}
							}
						}
					}
				}
			}
		}
	}
	return &PlaylistResult{
		PlaylistID:         playlistID,
		Title:              title,
		ChannelName:        channelName,
		ChannelID:          channelID,
		ThumbnailURL:       thumbURL,
		Thumbnails:         thumbs,
		VideoCountText:     videoCountText,
		VideoCount:         videoCount,
		IsCourse:           isCourse,
		Badges:             badges,
		DescriptionSnippet: desc,
	}
}

func parseOfficialCard(v interface{}) *TopicCard {
	m := asMap(v)
	if m == nil {
		return nil
	}
	header, _ := m["header"].(map[string]interface{})
	pageHeader, _ := header["pageHeaderViewModel"].(map[string]interface{})
	var title string
	var browseID string
	var avatarURL string
	var avatars []Thumbnail
	if pageHeader != nil {
		if t, ok := pageHeader["title"].(map[string]interface{}); ok {
			if dyn, ok := t["dynamicTextViewModel"].(map[string]interface{}); ok {
				if txt, ok := dyn["text"].(map[string]interface{}); ok {
					title, _ = txt["content"].(string)
				}
				if rc, ok := dyn["rendererContext"].(map[string]interface{}); ok {
					if cc, ok := rc["commandContext"].(map[string]interface{}); ok {
						if tap, ok := cc["onTap"].(map[string]interface{}); ok {
							if cmd, ok := tap["innertubeCommand"].(map[string]interface{}); ok {
								if be, ok := cmd["browseEndpoint"].(map[string]interface{}); ok {
									browseID, _ = be["browseId"].(string)
								}
							}
						}
					}
				}
			}
		}
		if img, ok := pageHeader["image"].(map[string]interface{}); ok {
			if cp, ok := img["contentPreviewImageViewModel"].(map[string]interface{}); ok {
				if im, ok := cp["image"].(map[string]interface{}); ok {
					avatars = parseSourcesThumbnails(im)
					if len(avatars) > 0 {
						avatarURL = avatars[len(avatars)-1].URL
					}
				}
			}
		}
	}
	if title == "" {
		title = getText(m["title"])
	}
	if title == "" && browseID == "" {
		return nil
	}
	if browseID == "" {
		browseID = "UCGllT9uTMh-nzEvBCi-9KRQ"
	}
	return &TopicCard{
		Title:     title,
		BrowseID:  browseID,
		AvatarURL: avatarURL,
		Avatars:   avatars,
	}
}

func extractContinuationToken(v interface{}) string {
	m := asMap(v)
	if m == nil {
		return ""
	}
	if ci, ok := m["continuationItemRenderer"].(map[string]interface{}); ok {
		if ep, ok := ci["continuationEndpoint"].(map[string]interface{}); ok {
			if cmd, ok := ep["continuationCommand"].(map[string]interface{}); ok {
				if tok, ok := cmd["token"].(string); ok {
					return tok
				}
			}
		}
	}
	return ""
}

func collect(json map[string]interface{}) ([]VideoMetadata, []ChannelResult, []ShortResult, []PlaylistResult, *TopicCard, string, string) {
	var videos []VideoMetadata
	var channels []ChannelResult
	var shorts []ShortResult
	var playlists []PlaylistResult
	var topicCard *TopicCard
	continuation := ""
	estimated := ""
	if er, ok := json["estimatedResults"].(string); ok {
		estimated = er
	}
	// page1
	if contents, ok := json["contents"].(map[string]interface{}); ok {
		if two, ok := contents["twoColumnSearchResultsRenderer"].(map[string]interface{}); ok {
			if primary, ok := two["primaryContents"].(map[string]interface{}); ok {
				if section, ok := primary["sectionListRenderer"].(map[string]interface{}); ok {
					if arr, ok := section["contents"].([]interface{}); ok {
						for _, sec := range arr {
							if tok := extractContinuationToken(sec); tok != "" {
								continuation = tok
								continue
							}
							if sm, ok := sec.(map[string]interface{}); ok {
								if itemSec, ok := sm["itemSectionRenderer"].(map[string]interface{}); ok {
									if items, ok := itemSec["contents"].([]interface{}); ok {
										for _, item := range items {
											if tok := extractContinuationToken(item); tok != "" {
												continuation = tok
												continue
											}
											if im, ok := item.(map[string]interface{}); ok {
												if vr, ok := im["videoRenderer"]; ok {
													if vm := parseVideoRenderer(vr); vm != nil {
														videos = append(videos, *vm)
													}
												} else if cr, ok := im["channelRenderer"]; ok {
													if ch := parseChannelRenderer(cr); ch != nil {
														channels = append(channels, *ch)
													}
												} else if card, ok := im["officialCardViewModel"]; ok {
													if topicCard == nil {
														topicCard = parseOfficialCard(card)
													}
												} else if lockup, ok := im["lockupViewModel"]; ok {
													if pl := parsePlaylistLockup(lockup); pl != nil {
														playlists = append(playlists, *pl)
													} else if ct, ok := lockup.(map[string]interface{}); ok && ct["contentType"] == "LOCKUP_CONTENT_TYPE_VIDEO" {
														// video lockup fallback — try videoRenderer-like parsing via lockup
														if vm := parseVideoRenderer(lockup); vm != nil {
															videos = append(videos, *vm)
														}
													}
												} else if grid, ok := im["gridShelfViewModel"]; ok {
													if gm, ok := grid.(map[string]interface{}); ok {
														if contents, ok := gm["contents"].([]interface{}); ok {
															for _, sh := range contents {
																if sm2, ok := sh.(map[string]interface{}); ok {
																	if sl, ok := sm2["shortsLockupViewModel"]; ok {
																		if s := parseShortLockup(sl); s != nil {
																			shorts = append(shorts, *s)
																		}
																	}
																}
															}
														}
													}
												} else if shelf, ok := im["shelfRenderer"]; ok {
													if shelfM, ok := shelf.(map[string]interface{}); ok {
														if content, ok := shelfM["content"].(map[string]interface{}); ok {
															if vl, ok := content["verticalListRenderer"].(map[string]interface{}); ok {
																if items2, ok := vl["items"].([]interface{}); ok {
																	for _, sub := range items2 {
																		if sm2, ok := sub.(map[string]interface{}); ok {
																			if vr, ok := sm2["videoRenderer"]; ok {
																				if vm := parseVideoRenderer(vr); vm != nil {
																					videos = append(videos, *vm)
																				}
																			} else if cr, ok := sm2["channelRenderer"]; ok {
																				if ch := parseChannelRenderer(cr); ch != nil {
																					channels = append(channels, *ch)
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
	// page2 continuation
	if cmds, ok := json["onResponseReceivedCommands"].([]interface{}); ok {
		for _, cmd := range cmds {
			if cm, ok := cmd.(map[string]interface{}); ok {
				if action, ok := cm["appendContinuationItemsAction"].(map[string]interface{}); ok {
					if items, ok := action["continuationItems"].([]interface{}); ok {
						for _, item := range items {
							if tok := extractContinuationToken(item); tok != "" {
								continuation = tok
								continue
							}
							if im, ok := item.(map[string]interface{}); ok {
								if itemSec, ok := im["itemSectionRenderer"].(map[string]interface{}); ok {
									if contents, ok := itemSec["contents"].([]interface{}); ok {
										for _, sub := range contents {
											if tok := extractContinuationToken(sub); tok != "" {
												continuation = tok
												continue
											}
											if sm, ok := sub.(map[string]interface{}); ok {
												if vr, ok := sm["videoRenderer"]; ok {
													if vm := parseVideoRenderer(vr); vm != nil {
														videos = append(videos, *vm)
													}
												} else if cr, ok := sm["channelRenderer"]; ok {
													if ch := parseChannelRenderer(cr); ch != nil {
														channels = append(channels, *ch)
													}
												} else if lockup, ok := sm["lockupViewModel"]; ok {
													if pl := parsePlaylistLockup(lockup); pl != nil {
														playlists = append(playlists, *pl)
													}
												} else if card, ok := sm["officialCardViewModel"]; ok {
													if topicCard == nil {
														topicCard = parseOfficialCard(card)
													}
												} else if grid, ok := sm["gridShelfViewModel"]; ok {
													if gm, ok := grid.(map[string]interface{}); ok {
														if contents, ok := gm["contents"].([]interface{}); ok {
															for _, sh := range contents {
																if sm2, ok := sh.(map[string]interface{}); ok {
																	if sl, ok := sm2["shortsLockupViewModel"]; ok {
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
										}
									}
								} else if vr, ok := im["videoRenderer"]; ok {
									if vm := parseVideoRenderer(vr); vm != nil {
										videos = append(videos, *vm)
									}
								} else if cr, ok := im["channelRenderer"]; ok {
									if ch := parseChannelRenderer(cr); ch != nil {
										channels = append(channels, *ch)
									}
								} else if lockup, ok := im["lockupViewModel"]; ok {
									if pl := parsePlaylistLockup(lockup); pl != nil {
										playlists = append(playlists, *pl)
									}
								} else if grid, ok := im["gridShelfViewModel"]; ok {
									if gm, ok := grid.(map[string]interface{}); ok {
										if contents, ok := gm["contents"].([]interface{}); ok {
											for _, sh := range contents {
												if sm2, ok := sh.(map[string]interface{}); ok {
													if sl, ok := sm2["shortsLockupViewModel"]; ok {
														if s := parseShortLockup(sl); s != nil {
															shorts = append(shorts, *s)
														}
													}
												}
											}
										}
									}
								} else if card, ok := im["officialCardViewModel"]; ok {
									if topicCard == nil {
										topicCard = parseOfficialCard(card)
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
		continuation = findToken(json)
	}
	return videos, channels, shorts, playlists, topicCard, continuation, estimated
}

// Search performs innertube search with session cookies, handles pagination via continuation
func Search(session *InnertubeSession, query string, continuation string) (*SearchResult, error) {
	if strings.TrimSpace(query) == "" && strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("query and continuation empty")
	}
	client := &http.Client{Timeout: 15 * time.Second}
	urlStr := fmt.Sprintf("https://www.youtube.com/youtubei/v1/search?prettyPrint=false&key=%s", session.APIKey)
	tz := "UTC"
	if idx := strings.Index(session.Pref, "tz="); idx != -1 {
		rest := session.Pref[idx+3:]
		if amp := strings.Index(rest, "&"); amp != -1 {
			tz = rest[:amp]
		} else {
			tz = rest
		}
	}
	var originalURL string
	if strings.TrimSpace(continuation) != "" {
		if strings.TrimSpace(query) == "" {
			originalURL = "https://www.youtube.com"
		} else {
			originalURL = "https://www.youtube.com/results?search_query=" + url.QueryEscape(query)
		}
	} else {
		originalURL = "https://www.youtube.com/results?search_query=" + url.QueryEscape(query)
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
		bodyMap["query"] = query
	}
	bodyBytes, _ := json.Marshal(bodyMap)
	referer := "https://www.youtube.com/"
	if strings.TrimSpace(query) != "" {
		referer = "https://www.youtube.com/results?search_query=" + url.QueryEscape(query)
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
		return nil, fmt.Errorf("search POST: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		buf := new(bytes.Buffer)
		buf.ReadFrom(resp.Body)
		s := buf.String()
		if len(s) > 500 {
			s = s[:500]
		}
		return nil, fmt.Errorf("search status %d: %s", resp.StatusCode, s)
	}
	var j map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&j); err != nil {
		return nil, fmt.Errorf("parse search json: %w", err)
	}
	videos, channels, shorts, playlists, topicCard, cont, estimated := collect(j)
	q := query
	if strings.TrimSpace(query) == "" {
		q = continuation
	}
	return &SearchResult{
		Query:            q,
		Videos:           videos,
		Channels:         channels,
		Shorts:           shorts,
		Playlists:        playlists,
		TopicCard:        topicCard,
		Continuation:     cont,
		EstimatedResults: estimated,
	}, nil
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
