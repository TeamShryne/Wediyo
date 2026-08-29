package wediyo

import (
	"encoding/json"
	"fmt"
	"strings"
)

// Mediyo flow port: comments are in frameworkUpdates.entityBatchUpdate.mutations[].payload.commentEntityPayload
// and thread continuations in onResponseReceivedEndpoints.*.continuationItems[].commentThreadRenderer

func commentEntities(resp map[string]interface{}) []map[string]interface{} {
	var out []map[string]interface{}
	fw, ok := resp["frameworkUpdates"].(map[string]interface{})
	if !ok {
		return out
	}
	eb, ok := fw["entityBatchUpdate"].(map[string]interface{})
	if !ok {
		return out
	}
	muts, ok := eb["mutations"].([]interface{})
	if !ok {
		return out
	}
	for _, m := range muts {
		if mm, ok := m.(map[string]interface{}); ok {
			key, _ := mm["entityKey"].(string)
			if payload, ok := mm["payload"].(map[string]interface{}); ok {
				if cep, ok := payload["commentEntityPayload"]; ok {
					if cem, ok := cep.(map[string]interface{}); ok {
						out = append(out, map[string]interface{}{"entityKey": key, "commentEntity": cem})
					} else {
						out = append(out, map[string]interface{}{"entityKey": key, "commentEntity": payload["commentEntityPayload"]})
					}
				}
			}
		}
	}
	return out
}

func buildThreadReplyMap(resp map[string]interface{}) map[string]string {
	m := make(map[string]string)
	endpoints, ok := resp["onResponseReceivedEndpoints"].([]interface{})
	if !ok {
		return m
	}
	for _, ep := range endpoints {
		if em, ok := ep.(map[string]interface{}); ok {
			var items []interface{}
			if cmd, ok := em["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
				if arr, ok := cmd["continuationItems"].([]interface{}); ok {
					items = arr
				}
			} else if act, ok := em["appendContinuationItemsAction"].(map[string]interface{}); ok {
				if arr, ok := act["continuationItems"].([]interface{}); ok {
					items = arr
				}
			}
			if items == nil {
				continue
			}
			for _, it := range items {
				if im, ok := it.(map[string]interface{}); ok {
					if ctr, ok := im["commentThreadRenderer"].(map[string]interface{}); ok {
						var entityKey string
						if vm, ok := ctr["commentViewModel"].(map[string]interface{}); ok {
							if inner, ok := vm["commentViewModel"].(map[string]interface{}); ok {
								entityKey, _ = inner["commentKey"].(string)
							} else {
								entityKey, _ = vm["commentKey"].(string)
							}
						}
						// also try direct
						if entityKey == "" {
							if vm, ok := ctr["commentViewModel"].(map[string]interface{}); ok {
								entityKey, _ = vm["commentKey"].(string)
							}
						}
						tok := extractRepliesToken(ctr)
						if entityKey != "" && tok != "" {
							m[entityKey] = tok
						}
					}
				}
			}
		}
	}
	return m
}

func extractRepliesToken(ctr map[string]interface{}) string {
	replies, ok := ctr["replies"].(map[string]interface{})
	if !ok {
		return ""
	}
	crr, ok := replies["commentRepliesRenderer"].(map[string]interface{})
	if !ok {
		return ""
	}
	subThreads, ok := crr["subThreads"].([]interface{})
	if !ok {
		return ""
	}
	for _, st := range subThreads {
		if sm, ok := st.(map[string]interface{}); ok {
			if cir, ok := sm["continuationItemRenderer"].(map[string]interface{}); ok {
				if ep, ok := cir["continuationEndpoint"].(map[string]interface{}); ok {
					if cmd, ok := ep["continuationCommand"].(map[string]interface{}); ok {
						if tok, ok := cmd["token"].(string); ok && tok != "" {
							return tok
						}
					}
				}
			}
		}
	}
	return ""
}

func extractContinuationTokenFromItem(item map[string]interface{}) string {
	cir, ok := item["continuationItemRenderer"].(map[string]interface{})
	if !ok {
		return ""
	}
	if ep, ok := cir["continuationEndpoint"].(map[string]interface{}); ok {
		if cmd, ok := ep["continuationCommand"].(map[string]interface{}); ok {
			if tok, ok := cmd["token"].(string); ok && tok != "" {
				return tok
			}
		}
	}
	if btn, ok := cir["button"].(map[string]interface{}); ok {
		if br, ok := btn["buttonRenderer"].(map[string]interface{}); ok {
			if cmd, ok := br["command"].(map[string]interface{}); ok {
				if cc, ok := cmd["continuationCommand"].(map[string]interface{}); ok {
					if tok, ok := cc["token"].(string); ok && tok != "" {
						return tok
					}
				}
			}
		}
	}
	return ""
}

func parseSortFilters(hdr map[string]interface{}) []CommentSortFilter {
	var out []CommentSortFilter
	sortMenu, ok := hdr["sortMenu"].(map[string]interface{})
	if !ok {
		return out
	}
	sf, ok := sortMenu["sortFilterSubMenuRenderer"].(map[string]interface{})
	if !ok {
		return out
	}
	items, ok := sf["subMenuItems"].([]interface{})
	if !ok {
		return out
	}
	for _, it := range items {
		if mm, ok := it.(map[string]interface{}); ok {
			title, _ := mm["title"].(string)
			cont := ""
			if c, ok := mm["continuation"].(map[string]interface{}); ok {
				if rcd, ok := c["reloadContinuationData"].(map[string]interface{}); ok {
					cont, _ = rcd["continuation"].(string)
				}
			}
			selected, _ := mm["selected"].(bool)
			subtitle, _ := mm["subtitle"].(string)
			if cont == "" {
				continue
			}
			out = append(out, CommentSortFilter{Title: title, Selected: selected, ContinuationToken: cont, Subtitle: subtitle})
		}
	}
	return out
}

func parseCommentsPage(resp map[string]interface{}) (*CommentsPage, error) {
	var count string
	var continuation string
	var sortFilters []CommentSortFilter
	commentEnts := commentEntities(resp)

	if endpoints, ok := resp["onResponseReceivedEndpoints"].([]interface{}); ok {
		for _, ep := range endpoints {
			if em, ok := ep.(map[string]interface{}); ok {
				if cmd, ok := em["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
					if items, ok := cmd["continuationItems"].([]interface{}); ok {
						for _, it := range items {
							if im, ok := it.(map[string]interface{}); ok {
								if hdr, ok := im["commentsHeaderRenderer"].(map[string]interface{}); ok {
									count = getText(hdr["countText"])
									if len(sortFilters) == 0 {
										sortFilters = parseSortFilters(hdr)
									}
								}
							}
						}
						if continuation == "" && len(items) > 0 {
							if last, ok := items[len(items)-1].(map[string]interface{}); ok {
								if tok := extractContinuationTokenFromItem(last); tok != "" {
									continuation = tok
								}
							}
						}
					}
				}
				if act, ok := em["appendContinuationItemsAction"].(map[string]interface{}); ok {
					if items, ok := act["continuationItems"].([]interface{}); ok && len(items) > 0 {
						if last, ok := items[len(items)-1].(map[string]interface{}); ok {
							if tok := extractContinuationTokenFromItem(last); tok != "" {
								continuation = tok
							}
						}
					}
				}
				// also check reloadContinuationItemsCommand last for pagination
				if cmd, ok := em["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
					if items, ok := cmd["continuationItems"].([]interface{}); ok && len(items) > 0 {
						if continuation == "" {
							if last, ok := items[len(items)-1].(map[string]interface{}); ok {
								if tok := extractContinuationTokenFromItem(last); tok != "" {
									continuation = tok
								}
							}
						}
					}
				}
			}
		}
	}

	threadMap := buildThreadReplyMap(resp)
	var comments []Comment
	for _, ent := range commentEnts {
		cepRaw, _ := ent["commentEntity"].(map[string]interface{})
		if cepRaw == nil {
			continue
		}
		// commentEntity is directly the payload map
		cep := cepRaw
		commentID := ""
		if v := getNested(cep, "properties", "commentId"); v != nil {
			commentID, _ = v.(string)
		}
		content := ""
		if v := getNested(cep, "properties", "content", "content"); v != nil {
			content, _ = v.(string)
		}
		publishedTime := ""
		if v := getNested(cep, "properties", "publishedTime"); v != nil {
			publishedTime, _ = v.(string)
		}
		replyLevel := 0
		if v := getNested(cep, "properties", "replyLevel"); v != nil {
			if f, ok := v.(float64); ok {
				replyLevel = int(f)
			}
		}
		author := CommentAuthor{}
		if v := getNested(cep, "author", "channelId"); v != nil {
			author.ChannelID, _ = v.(string)
		}
		if v := getNested(cep, "author", "displayName"); v != nil {
			author.Name, _ = v.(string)
		}
		if v := getNested(cep, "author", "avatarThumbnailUrl"); v != nil {
			author.Avatar, _ = v.(string)
		}
		if v := getNested(cep, "author", "isVerified"); v != nil {
			author.IsVerified, _ = v.(bool)
		}
		if v := getNested(cep, "author", "isCreator"); v != nil {
			author.IsCreator, _ = v.(bool)
		}
		if v := getNested(cep, "author", "isArtist"); v != nil {
			author.IsArtist, _ = v.(bool)
		}
		likeCount := ""
		if v := getNested(cep, "toolbar", "likeCountNotliked"); v != nil {
			likeCount, _ = v.(string)
		}
		replyCount := ""
		if v := getNested(cep, "toolbar", "replyCount"); v != nil {
			replyCount, _ = v.(string)
		}
		entityKey, _ := ent["entityKey"].(string)
		repliesContinuation := ""
		if replyLevel == 0 {
			repliesContinuation = threadMap[entityKey]
		}
		comments = append(comments, Comment{
			CommentID:           commentID,
			Content:             content,
			PublishedTime:       publishedTime,
			Author:              author,
			LikeCount:           likeCount,
			ReplyCount:          replyCount,
			ReplyLevel:          replyLevel,
			RepliesContinuation: repliesContinuation,
		})
	}

	return &CommentsPage{Count: count, Comments: comments, Continuation: continuation, SortFilters: sortFilters}, nil
}

func parseReplyContinuation(resp map[string]interface{}) (*CommentsPage, error) {
	var continuation string
	if endpoints, ok := resp["onResponseReceivedEndpoints"].([]interface{}); ok {
		for _, ep := range endpoints {
			if em, ok := ep.(map[string]interface{}); ok {
				if act, ok := em["appendContinuationItemsAction"].(map[string]interface{}); ok {
					if items, ok := act["continuationItems"].([]interface{}); ok && len(items) > 0 {
						if last, ok := items[len(items)-1].(map[string]interface{}); ok {
							if tok := extractContinuationTokenFromItem(last); tok != "" {
								continuation = tok
							}
						}
					}
				}
			}
		}
	}
	var comments []Comment
	ents := commentEntities(resp)
	for _, ent := range ents {
		cepRaw, _ := ent["commentEntity"].(map[string]interface{})
		if cepRaw == nil {
			continue
		}
		cep := cepRaw
		commentID := ""
		if v := getNested(cep, "properties", "commentId"); v != nil {
			commentID, _ = v.(string)
		}
		content := ""
		if v := getNested(cep, "properties", "content", "content"); v != nil {
			content, _ = v.(string)
		}
		publishedTime := ""
		if v := getNested(cep, "properties", "publishedTime"); v != nil {
			publishedTime, _ = v.(string)
		}
		replyLevel := 0
		if v := getNested(cep, "properties", "replyLevel"); v != nil {
			if f, ok := v.(float64); ok {
				replyLevel = int(f)
			}
		}
		author := CommentAuthor{}
		if v := getNested(cep, "author", "channelId"); v != nil {
			author.ChannelID, _ = v.(string)
		}
		if v := getNested(cep, "author", "displayName"); v != nil {
			author.Name, _ = v.(string)
		}
		if v := getNested(cep, "author", "avatarThumbnailUrl"); v != nil {
			author.Avatar, _ = v.(string)
		}
		if v := getNested(cep, "author", "isVerified"); v != nil {
			author.IsVerified, _ = v.(bool)
		}
		if v := getNested(cep, "author", "isCreator"); v != nil {
			author.IsCreator, _ = v.(bool)
		}
		if v := getNested(cep, "author", "isArtist"); v != nil {
			author.IsArtist, _ = v.(bool)
		}
		likeCount := ""
		if v := getNested(cep, "toolbar", "likeCountNotliked"); v != nil {
			likeCount, _ = v.(string)
		}
		replyCount := ""
		if v := getNested(cep, "toolbar", "replyCount"); v != nil {
			replyCount, _ = v.(string)
		}
		comments = append(comments, Comment{
			CommentID:     commentID,
			Content:       content,
			PublishedTime: publishedTime,
			Author:        author,
			LikeCount:     likeCount,
			ReplyCount:    replyCount,
			ReplyLevel:    replyLevel,
		})
	}
	return &CommentsPage{Comments: comments, Continuation: continuation}, nil
}

func extractCommentsTokenFromNext(j map[string]interface{}) string {
	// engagementPanels path for WEB watch: engagementPanels[].engagementPanelSectionListRenderer.panelIdentifier == engagement-panel-comments-section
	if eps, ok := j["engagementPanels"].([]interface{}); ok {
		for _, ep := range eps {
			if em, ok := ep.(map[string]interface{}); ok {
				if r, ok := em["engagementPanelSectionListRenderer"].(map[string]interface{}); ok {
					if pid, ok := r["panelIdentifier"].(string); ok && pid == "engagement-panel-comments-section" {
						if content, ok := r["content"].(map[string]interface{}); ok {
							if slr, ok := content["sectionListRenderer"].(map[string]interface{}); ok {
								if contents, ok := slr["contents"].([]interface{}); ok && len(contents) > 0 {
									if isr, ok := contents[0].(map[string]interface{})["itemSectionRenderer"].(map[string]interface{}); ok {
										if arr, ok := isr["contents"].([]interface{}); ok && len(arr) > 0 {
											if cir, ok := arr[0].(map[string]interface{})["continuationItemRenderer"].(map[string]interface{}); ok {
												if ep2, ok := cir["continuationEndpoint"].(map[string]interface{}); ok {
													if cmd, ok := ep2["continuationCommand"].(map[string]interface{}); ok {
														if tok, ok := cmd["token"].(string); ok {
															return tok
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
	// Fallback generic search for any token containing "comment"
	var find func(v interface{}) string
	find = func(v interface{}) string {
		if m, ok := v.(map[string]interface{}); ok {
			if cir, ok := m["continuationItemRenderer"].(map[string]interface{}); ok {
				if ep, ok := cir["continuationEndpoint"].(map[string]interface{}); ok {
					if cmd, ok := ep["continuationCommand"].(map[string]interface{}); ok {
						if tok, ok := cmd["token"].(string); ok && strings.Contains(tok, "comments-section") {
							return tok
						}
					}
				}
			}
			for _, val := range m {
				if res := find(val); res != "" {
					return res
				}
			}
		} else if arr, ok := v.([]interface{}); ok {
			for _, el := range arr {
				if res := find(el); res != "" {
					return res
				}
			}
		}
		return ""
	}
	if tok := find(j); tok != "" {
		return tok
	}
	return ""
}

func extractRelatedContinuationFromNext(j map[string]interface{}) string {
	// twoColumnWatchNextResults.secondaryResults.secondaryResults.results[0].itemSectionRenderer.contents last is continuationItemRenderer
	if contents, ok := j["contents"].(map[string]interface{}); ok {
		if two, ok := contents["twoColumnWatchNextResults"].(map[string]interface{}); ok {
			if sec, ok := two["secondaryResults"].(map[string]interface{}); ok {
				if inner, ok := sec["secondaryResults"].(map[string]interface{}); ok {
					if results, ok := inner["results"].([]interface{}); ok && len(results) > 0 {
						if first, ok := results[0].(map[string]interface{}); ok {
							if isr, ok := first["itemSectionRenderer"].(map[string]interface{}); ok {
								if arr, ok := isr["contents"].([]interface{}); ok && len(arr) > 0 {
									if last, ok := arr[len(arr)-1].(map[string]interface{}); ok {
										if cir, ok := last["continuationItemRenderer"].(map[string]interface{}); ok {
											if ep, ok := cir["continuationEndpoint"].(map[string]interface{}); ok {
												if cmd, ok := ep["continuationCommand"].(map[string]interface{}); ok {
													if tok, ok := cmd["token"].(string); ok {
														return tok
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
	return ""
}

// API functions

func FetchCommentsPage(session *InnertubeSession, continuation string) (*CommentsPage, error) {
	if strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("continuation empty")
	}
	body := map[string]interface{}{"continuation": continuation}
	resp, _, err := postInnertube(session, "next", body)
	if err != nil {
		return nil, err
	}
	// Debug helper
	_ = json.RawMessage{}
	return parseCommentsPage(resp)
}

func FetchRepliesPage(session *InnertubeSession, continuation string) (*CommentsPage, error) {
	if strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("continuation empty")
	}
	body := map[string]interface{}{"continuation": continuation}
	resp, _, err := postInnertube(session, "next", body)
	if err != nil {
		return nil, err
	}
	return parseReplyContinuation(resp)
}

func FetchRelatedContinuation(session *InnertubeSession, continuation string) (*VideoDetailResult, error) {
	if strings.TrimSpace(continuation) == "" {
		return nil, fmt.Errorf("continuation empty")
	}
	body := map[string]interface{}{"continuation": continuation}
	resp, _, err := postInnertube(session, "next", body)
	if err != nil {
		return nil, err
	}
	// Related continuation is in appendContinuationItemsAction / reloadContinuationItemsCommand
	// Try generic next parsing for related
	var related []VideoMetadata
	var cont string
	if acts, ok := resp["onResponseReceivedEndpoints"].([]interface{}); ok {
		for _, a := range acts {
			if am, ok := a.(map[string]interface{}); ok {
				if cmd, ok := am["appendContinuationItemsAction"].(map[string]interface{}); ok {
					if items, ok := cmd["continuationItems"].([]interface{}); ok {
						for _, it := range items {
							if im, ok := it.(map[string]interface{}); ok {
								if lvm, ok := im["lockupViewModel"]; ok {
									if vm := parseLockupToVideo(lvm); vm != nil {
										related = append(related, *vm)
									}
								} else if _, ok := im["continuationItemRenderer"]; ok {
									cont = extractContinuationTokenFromItem(im)
								}
							}
						}
					}
				}
				if cmd, ok := am["reloadContinuationItemsCommand"].(map[string]interface{}); ok {
					if items, ok := cmd["continuationItems"].([]interface{}); ok {
						for _, it := range items {
							if im, ok := it.(map[string]interface{}); ok {
								if _, ok := im["continuationItemRenderer"]; ok {
									if tok := extractContinuationTokenFromItem(im); tok != "" {
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
	// Also handle frameworkUpdates? related may be in contents?
	if len(related) == 0 {
		// fallback parse via generic
		dummy := &VideoDetailResult{}
		parseNextDetail(resp, dummy)
		related = dummy.RelatedVideos
		cont = dummy.RelatedContinuation
	}
	return &VideoDetailResult{RelatedVideos: related, RelatedContinuation: cont}, nil
}
