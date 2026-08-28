package wediyo

import (
	"fmt"
	"testing"
)

func makeLockupPage(start, count int, next string) map[string]interface{} {
	items := []interface{}{}
	for i := 0; i < count; i++ {
		vid := fmt.Sprintf("VID%04d", start+i)
		items = append(items, map[string]interface{}{
			"lockupViewModel": map[string]interface{}{
				"contentId":   vid,
				"contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
				"contentImage": map[string]interface{}{
					"thumbnailViewModel": map[string]interface{}{
						"image": map[string]interface{}{
							"sources": []interface{}{
								map[string]interface{}{"url": "https://i.ytimg.com/vi/" + vid + "/hqdefault.jpg", "width": 336.0, "height": 188.0},
							},
						},
					},
				},
				"metadata": map[string]interface{}{
					"lockupMetadataViewModel": map[string]interface{}{
						"title": map[string]interface{}{"content": fmt.Sprintf("Video %d", start+i)},
					},
				},
			},
		})
	}
	if next != "" {
		items = append(items, map[string]interface{}{
			"continuationItemViewModel": map[string]interface{}{
				"continuationCommand": map[string]interface{}{
					"innertubeCommand": map[string]interface{}{
						"continuationCommand": map[string]interface{}{"token": next},
					},
				},
			},
		})
	}
	return map[string]interface{}{
		"onResponseReceivedActions": []interface{}{
			map[string]interface{}{
				"appendContinuationItemsAction": map[string]interface{}{
					"continuationItems": items,
				},
			},
		},
	}
}

func TestLargePlaylist288Go(t *testing.T) {
	p1 := makeLockupPage(1, 100, "c2")
	r1, _ := collectPlaylistDetail(p1)
	if len(r1.Videos) != 100 {
		t.Fatalf("p1 %d", len(r1.Videos))
	}
	p2 := makeLockupPage(101, 100, "c3")
	r2, _ := collectPlaylistDetail(p2)
	if len(r2.Videos) != 100 {
		t.Fatalf("p2 %d", len(r2.Videos))
	}
	p3 := makeLockupPage(201, 88, "")
	r3, _ := collectPlaylistDetail(p3)
	if len(r3.Videos) != 88 {
		t.Fatalf("p3 %d", len(r3.Videos))
	}
	total := len(r1.Videos) + len(r2.Videos) + len(r3.Videos)
	if total != 288 {
		t.Fatalf("total %d", total)
	}
	t.Logf("288 playlist OK")
}

func TestLargePodcast500Go(t *testing.T) {
	total := 0
	for i := 0; i < 5; i++ {
		next := ""
		if i < 4 {
			next = fmt.Sprintf("cont%d", i+2)
		}
		p := makeLockupPage(i*100+1, 100, next)
		r, _ := collectPodcastDetail(p)
		if len(r.Episodes) != 100 {
			t.Fatalf("page %d %d", i+1, len(r.Episodes))
		}
		total += len(r.Episodes)
	}
	if total != 500 {
		t.Fatalf("total %d", total)
	}
	t.Logf("500 podcast OK")
}

func TestLargeCourse130Go(t *testing.T) {
	p1 := makeLockupPage(1, 100, "c2")
	r1, _ := collectPlaylistDetail(p1)
	p2 := makeLockupPage(101, 30, "")
	r2, _ := collectPlaylistDetail(p2)
	total := len(r1.Videos) + len(r2.Videos)
	if total != 130 {
		t.Fatalf("total %d", total)
	}
	t.Logf("130 course OK")
}
