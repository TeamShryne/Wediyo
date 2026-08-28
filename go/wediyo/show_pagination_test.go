package wediyo

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"testing"
)

func TestShowDetailContinuationSynthetic(t *testing.T) {
	// Synthetic continuation response mimicking YouTube's onResponseReceivedActions for shows (legacy playlistVideoRenderer)
	// This tests that collectShowDetail correctly handles pagination (len(episodes)==0 fallback)
	j := map[string]interface{}{
		"onResponseReceivedActions": []interface{}{
			map[string]interface{}{
				"appendContinuationItemsAction": map[string]interface{}{
					"continuationItems": []interface{}{
						map[string]interface{}{
							"playlistVideoRenderer": map[string]interface{}{
								"videoId": "CONTINUE1",
								"title":   map[string]interface{}{"simpleText": "Continue Episode 51"},
								"lengthText": map[string]interface{}{"simpleText": "42:00"},
								"lengthSeconds": "2520",
								"index": map[string]interface{}{"simpleText": "51"},
								"thumbnail": map[string]interface{}{
									"thumbnails": []interface{}{map[string]interface{}{"url": "https://i.ytimg.com/vi/CONTINUE1/hqdefault.jpg", "width": 480.0, "height": 360.0}},
								},
								"isPlayable": true,
							},
						},
						map[string]interface{}{
							"playlistVideoRenderer": map[string]interface{}{
								"videoId": "CONTINUE2",
								"title":   map[string]interface{}{"simpleText": "Continue Episode 52"},
								"lengthText": map[string]interface{}{"simpleText": "38:11"},
								"lengthSeconds": "2291",
								"index": map[string]interface{}{"simpleText": "52"},
								"thumbnail": map[string]interface{}{
									"thumbnails": []interface{}{map[string]interface{}{"url": "https://i.ytimg.com/vi/CONTINUE2/hqdefault.jpg", "width": 480.0, "height": 360.0}},
								},
								"isPlayable": true,
							},
						},
						map[string]interface{}{
							"continuationItemRenderer": map[string]interface{}{
								"continuationEndpoint": map[string]interface{}{
									"continuationCommand": map[string]interface{}{"token": "NEXT_CONT_TOKEN_123"},
								},
							},
						},
					},
				},
			},
		},
	}
	res, err := collectShowDetail(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Episodes) != 2 {
		t.Fatalf("episodes %d want 2 got %+v", len(res.Episodes), res.Episodes)
	}
	if res.Episodes[0].VideoId != "CONTINUE1" || res.Episodes[1].VideoId != "CONTINUE2" {
		t.Fatalf("ids %+v", res.Episodes)
	}
	if res.Continuation != "NEXT_CONT_TOKEN_123" {
		t.Fatalf("cont %q want NEXT_CONT_TOKEN_123", res.Continuation)
	}
	if res.Episodes[0].DurationText != "42:00" || res.Episodes[0].DurationSecs != 2520 {
		t.Fatalf("duration %+v", res.Episodes[0])
	}
	t.Logf("show continuation legacy: episodes %d cont %s", len(res.Episodes), res.Continuation)
}

func TestShowDetailContinuationLockupSynthetic(t *testing.T) {
	// New UI lockupViewModel pagination (like podcast.json)
	j := map[string]interface{}{
		"onResponseReceivedActions": []interface{}{
			map[string]interface{}{
				"appendContinuationItemsAction": map[string]interface{}{
					"continuationItems": []interface{}{
						map[string]interface{}{
							"lockupViewModel": map[string]interface{}{
								"contentId": "LOCKUP1",
								"contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
								"contentImage": map[string]interface{}{
									"thumbnailViewModel": map[string]interface{}{
										"image": map[string]interface{}{
											"sources": []interface{}{
												map[string]interface{}{"url": "https://i.ytimg.com/vi/LOCKUP1/hqdefault.jpg", "width": 336.0, "height": 188.0},
											},
										},
										"overlays": []interface{}{
											map[string]interface{}{
												"thumbnailBottomOverlayViewModel": map[string]interface{}{
													"badges": []interface{}{
														map[string]interface{}{"thumbnailBadgeViewModel": map[string]interface{}{"text": "1:23:45"}},
													},
												},
											},
										},
									},
								},
								"metadata": map[string]interface{}{
									"lockupMetadataViewModel": map[string]interface{}{
										"title": map[string]interface{}{"content": "Lockup Episode 1"},
									},
								},
							},
						},
						map[string]interface{}{
							"lockupViewModel": map[string]interface{}{
								"contentId": "LOCKUP2",
								"contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
								"contentImage": map[string]interface{}{
									"thumbnailViewModel": map[string]interface{}{
										"image": map[string]interface{}{
											"sources": []interface{}{
												map[string]interface{}{"url": "https://i.ytimg.com/vi/LOCKUP2/hqdefault.jpg", "width": 336.0, "height": 188.0},
											},
										},
									},
								},
								"metadata": map[string]interface{}{
									"lockupMetadataViewModel": map[string]interface{}{
										"title": map[string]interface{}{"content": "Lockup Episode 2"},
									},
								},
							},
						},
						map[string]interface{}{
							"continuationItemViewModel": map[string]interface{}{
								"continuationCommand": map[string]interface{}{
									"innertubeCommand": map[string]interface{}{
										"continuationCommand": map[string]interface{}{"token": "LOCKUP_NEXT_TOKEN"},
									},
								},
							},
						},
					},
				},
			},
		},
	}
	res, err := collectShowDetail(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Episodes) != 2 {
		t.Fatalf("episodes %d want 2 got %+v", len(res.Episodes), res.Episodes)
	}
	if res.Episodes[0].VideoId != "LOCKUP1" || res.Episodes[1].VideoId != "LOCKUP2" {
		t.Fatalf("ids %+v", res.Episodes)
	}
	if res.Episodes[0].Title != "Lockup Episode 1" {
		t.Fatalf("title %q", res.Episodes[0].Title)
	}
	if res.Episodes[0].DurationText != "1:23:45" {
		t.Fatalf("duration %q want 1:23:45 got %+v", res.Episodes[0].DurationText, res.Episodes[0])
	}
	if res.Continuation != "LOCKUP_NEXT_TOKEN" {
		t.Fatalf("cont %q want LOCKUP_NEXT_TOKEN", res.Continuation)
	}
	t.Logf("show continuation lockup: episodes %d cont %s first %s dur %s", len(res.Episodes), res.Continuation, res.Episodes[0].Title, res.Episodes[0].DurationText)
}

func TestShowDetailPaginationMerge(t *testing.T) {
	// Verify that initial page + continuation page can be merged as ViewModel does
	p1 := filepath.Join("..", "..", "research", "shows", "shows.json")
	data, err := os.ReadFile(p1)
	if err != nil {
		t.Skip("no show fixture")
	}
	var j1 map[string]interface{}
	json.Unmarshal(data, &j1)
	res1, err := collectShowDetail(j1)
	if err != nil {
		t.Fatalf("collect p1 %v", err)
	}
	if len(res1.Episodes) < 50 {
		t.Fatalf("p1 episodes %d", len(res1.Episodes))
	}
	if res1.Continuation == "" {
		t.Fatal("p1 continuation empty, cannot test pagination merge")
	}
	// Simulate continuation fetch returning 2 more episodes (using synthetic)
	contJSON := map[string]interface{}{
		"onResponseReceivedActions": []interface{}{
			map[string]interface{}{
				"appendContinuationItemsAction": map[string]interface{}{
					"continuationItems": []interface{}{
						map[string]interface{}{
							"playlistVideoRenderer": map[string]interface{}{
								"videoId": "MERGE1",
								"title":   map[string]interface{}{"simpleText": "Merged Episode 51"},
								"lengthText": map[string]interface{}{"simpleText": "45:00"},
								"index": map[string]interface{}{"simpleText": "51"},
								"thumbnail": map[string]interface{}{
									"thumbnails": []interface{}{map[string]interface{}{"url": "https://i.ytimg.com/vi/MERGE1/hqdefault.jpg", "width": 480.0, "height": 360.0}},
								},
								"isPlayable": true,
							},
						},
						map[string]interface{}{
							"continuationItemRenderer": map[string]interface{}{
								"continuationEndpoint": map[string]interface{}{
									"continuationCommand": map[string]interface{}{"token": ""},
								},
							},
						},
					},
				},
			},
		},
	}
	res2, err := collectShowDetail(contJSON)
	if err != nil {
		t.Fatalf("collect p2 %v", err)
	}
	if len(res2.Episodes) != 1 {
		t.Fatalf("p2 episodes %d want 1", len(res2.Episodes))
	}
	// Simulate ViewModel merge: episodes = p1 + p2, continuation = p2.continuation (empty means end)
	merged := append(res1.Episodes, res2.Episodes...)
	if len(merged) != 51 {
		t.Fatalf("merged %d want 51", len(merged))
	}
	if merged[50].VideoId != "MERGE1" {
		t.Fatalf("merged last id %q want MERGE1", merged[50].VideoId)
	}
	t.Logf("pagination merge: p1 %d + p2 %d = %d cont p1=%s p2='%s'", len(res1.Episodes), len(res2.Episodes), len(merged), res1.Continuation[:20], res2.Continuation)
}

func TestShowLoadAll187(t *testing.T) {
	// Simulate loading all 187 episodes of Shark Tank S1 via pagination loop (as ViewModel does)
	// Page1 is 50 from fixture, then we need 137 more to reach 187. We simulate 2 continuation pages: 87 + 50
	p1Path := filepath.Join("..", "..", "research", "shows", "shows.json")
	data, _ := os.ReadFile(p1Path)
	var j1 map[string]interface{}
	json.Unmarshal(data, &j1)
	res1, _ := collectShowDetail(j1)
	if len(res1.Episodes) != 50 {
		t.Fatalf("p1 want 50 got %d", len(res1.Episodes))
	}
	// Build synthetic page2 with 87 episodes (to reach 137), page3 with 50 to reach 187
	makePage := func(start, count int, nextToken string) map[string]interface{} {
		items := []interface{}{}
		for i := 0; i < count; i++ {
			vid := fmt.Sprintf("VID%03d", start+i)
			items = append(items, map[string]interface{}{
				"playlistVideoRenderer": map[string]interface{}{
					"videoId": vid,
					"title": map[string]interface{}{"simpleText": fmt.Sprintf("Episode %d", start+i)},
					"lengthText": map[string]interface{}{"simpleText": "40:00"},
					"index": map[string]interface{}{"simpleText": fmt.Sprintf("%d", start+i)},
					"thumbnail": map[string]interface{}{"thumbnails": []interface{}{map[string]interface{}{"url": "https://i.ytimg.com/vi/" + vid + "/hqdefault.jpg", "width": 480.0, "height": 360.0}}},
					"isPlayable": true,
				},
			})
		}
		if nextToken != "" {
			items = append(items, map[string]interface{}{
				"continuationItemRenderer": map[string]interface{}{
					"continuationEndpoint": map[string]interface{}{"continuationCommand": map[string]interface{}{"token": nextToken}},
				},
			})
		}
		return map[string]interface{}{
			"onResponseReceivedActions": []interface{}{
				map[string]interface{}{
					"appendContinuationItemsAction": map[string]interface{}{"continuationItems": items},
				},
			},
		}
	}
	// Page2: 87 episodes, next token
	p2JSON := makePage(51, 87, "TOKEN_PAGE3")
	res2, _ := collectShowDetail(p2JSON)
	if len(res2.Episodes) != 87 {
		t.Fatalf("p2 want 87 got %d", len(res2.Episodes))
	}
	if res2.Continuation != "TOKEN_PAGE3" {
		t.Fatalf("p2 cont %q", res2.Continuation)
	}
	// Page3: 50 episodes, no continuation (end)
	p3JSON := makePage(138, 50, "")
	res3, _ := collectShowDetail(p3JSON)
	if len(res3.Episodes) != 50 {
		t.Fatalf("p3 want 50 got %d", len(res3.Episodes))
	}
	if res3.Continuation != "" {
		t.Fatalf("p3 cont want empty got %q", res3.Continuation)
	}
	// Merge as ViewModel does
	all := append(append(res1.Episodes, res2.Episodes...), res3.Episodes...)
	if len(all) != 187 {
		t.Fatalf("total want 187 got %d", len(all))
	}
	t.Logf("load all 187: p1 %d + p2 %d + p3 %d = %d, last %s", len(res1.Episodes), len(res2.Episodes), len(res3.Episodes), len(all), all[186].VideoId)
}

func TestPodcastDetailPagination(t *testing.T) {
	// Podcast fixture has 100+ episodes and continuationItemViewModel
	p := filepath.Join("..", "..", "research", "pods", "podcast.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no podcast fixture")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectPodcastDetail(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Episodes) < 80 {
		t.Fatalf("episodes %d want >=80", len(res.Episodes))
	}
	if res.Continuation == "" {
		t.Fatal("podcast continuation empty")
	}
	if res.Header == nil || res.Header.Title != "Figuring Out With Raj Shamani" {
		t.Fatalf("header %+v", res.Header)
	}
	// Verify lockup parsing preserves duration
	if res.Episodes[0].DurationText == "" {
		t.Fatalf("first ep duration empty %+v", res.Episodes[0])
	}
	t.Logf("podcast %s episodes %d cont %s first %s dur %s", res.Header.Title, len(res.Episodes), res.Continuation[:30], res.Episodes[0].Title, res.Episodes[0].DurationText)

	// Synthetic continuation for podcast
	contJSON := map[string]interface{}{
		"onResponseReceivedActions": []interface{}{
			map[string]interface{}{
				"appendContinuationItemsAction": map[string]interface{}{
					"continuationItems": []interface{}{
						map[string]interface{}{
							"itemSectionRenderer": map[string]interface{}{
								"contents": []interface{}{
									map[string]interface{}{
										"lockupViewModel": map[string]interface{}{
											"contentId": "PODCAST_CONT1",
											"contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
											"contentImage": map[string]interface{}{
												"thumbnailViewModel": map[string]interface{}{
													"image": map[string]interface{}{
														"sources": []interface{}{
															map[string]interface{}{"url": "https://i.ytimg.com/vi/PODCAST_CONT1/hqdefault.jpg", "width": 336.0, "height": 188.0},
														},
													},
												},
											},
											"metadata": map[string]interface{}{
												"lockupMetadataViewModel": map[string]interface{}{
													"title": map[string]interface{}{"content": "Podcast Continue 1"},
												},
											},
										},
									},
								},
							},
						},
						map[string]interface{}{
							"continuationItemViewModel": map[string]interface{}{
								"continuationCommand": map[string]interface{}{
									"innertubeCommand": map[string]interface{}{
										"continuationCommand": map[string]interface{}{"token": "PODCAST_NEXT"},
									},
								},
							},
						},
					},
				},
			},
		},
	}
	res2, err := collectPodcastDetail(contJSON)
	if err != nil {
		t.Fatalf("collect cont %v", err)
	}
	if len(res2.Episodes) != 1 || res2.Episodes[0].VideoId != "PODCAST_CONT1" {
		t.Fatalf("cont episodes %+v", res2.Episodes)
	}
	if res2.Continuation != "PODCAST_NEXT" {
		t.Fatalf("cont token %q", res2.Continuation)
	}
	t.Logf("podcast continuation: %d episodes cont %s", len(res2.Episodes), res2.Continuation)
}
