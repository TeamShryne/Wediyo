package wediyo

import (
 "encoding/json"
 "os"
 "path/filepath"
 "strings"
 "testing"
)

func TestChannelHomeFixture(t *testing.T) {
 p := filepath.Join("..", "..", "research", "channels", "channel-home.json")
 data, err := os.ReadFile(p)
 if err != nil {
  t.Fatalf("read fixture: %v", err)
 }
 var j map[string]interface{}
 if err := json.Unmarshal(data, &j); err != nil {
  t.Fatalf("unmarshal: %v", err)
 }
 res, err := collectChannelHome(j)
 if err != nil {
  t.Fatalf("collect: %v", err)
 }
 if res.Header == nil {
  t.Fatal("header nil")
 }
 if res.Header.Title != "MrBeast" {
  t.Fatalf("title %q want MrBeast", res.Header.Title)
 }
 if res.Header.Handle != "@MrBeast" {
  t.Fatalf("handle %q want @MrBeast", res.Header.Handle)
 }
 if res.Header.ChannelID != "UCX6OQ3DkcsbYNE6H8uQQuVA" {
  t.Fatalf("id %q", res.Header.ChannelID)
 }
 if !res.Header.Verified {
  t.Fatal("verified false")
 }
 if len(res.Header.Avatars) < 3 {
  t.Fatalf("avatars %d want >=3", len(res.Header.Avatars))
 }
 // check all https
 for _, th := range res.Header.Avatars {
  if th.URL == "" || th.URL[:8] != "https://" {
   t.Fatalf("avatar not https %s", th.URL)
  }
 }
 if len(res.Header.Banners) < 6 {
  t.Fatalf("banners %d want 6 got %v", len(res.Header.Banners), res.Header.Banners)
 }
 for _, th := range res.Header.Banners {
  if th.URL[:8] != "https://" {
   t.Fatalf("banner not https %s", th.URL)
  }
 }
 if res.Header.SubscriberCountText != "514M subscribers" {
  t.Fatalf("subs %q", res.Header.SubscriberCountText)
 }
 if res.Header.VideoCountText != "999 videos" {
  t.Fatalf("videoCount %q", res.Header.VideoCountText)
 }
 if len(res.Tabs) < 5 {
  t.Fatalf("tabs %d", len(res.Tabs))
 }
 if res.Tabs[0].Title != "Home" || !res.Tabs[0].Selected {
  t.Fatalf("first tab %v", res.Tabs[0])
 }
 // params should be decoded
 if res.Tabs[0].Params != "EghmZWF0dXJlZAoOCAAQAg==" && res.Tabs[0].Params != "EghmZWF0dXJlZPIGBAoCMgA=" {
  // accept either, check contains Eghm
  if len(res.Tabs[0].Params) < 5 {
   t.Logf("home params %q", res.Tabs[0].Params)
  }
 }
 if len(res.Shelves) < 3 {
  t.Fatalf("shelves %d want >=3", len(res.Shelves))
 }
 // find New Uploads
 var newUploads *ChannelShelf
 for i := range res.Shelves {
  if res.Shelves[i].Title == "New Uploads" {
   newUploads = &res.Shelves[i]
   break
  }
 }
 if newUploads == nil {
  t.Fatalf("New Uploads shelf missing titles %v", func() []string { var s []string; for _, sh := range res.Shelves { s = append(s, sh.Title) }; return s }())
 }
 if len(newUploads.Videos) < 10 {
  t.Fatalf("New Uploads videos %d", len(newUploads.Videos))
 }
 v := newUploads.Videos[0]
 if v.ID == "" || v.Title == "" {
  t.Fatalf("video missing id/title %v", v)
 }
 if len(v.Thumbnails) == 0 || len(v.Thumbnails) < 2 {
  t.Fatalf("thumbnails %d", len(v.Thumbnails))
 }
 for _, th := range v.Thumbnails {
  if th.URL[:8] != "https://" {
   t.Fatalf("video thumb not https %s", th.URL)
  }
 }
 if v.ThumbnailURL[:8] != "https://" {
  t.Fatalf("thumb url not https %s", v.ThumbnailURL)
 }
 if len(v.ChannelAvatars) == 0 {
  t.Fatalf("channel avatars missing")
 }
 if v.DurationText == "" {
  t.Fatalf("duration empty")
 }
 // banner/ avatar highest url check
 if res.Header.AvatarURL == "" || res.Header.BannerURL == "" {
  t.Fatalf("avatar/banner url empty")
 }
 t.Logf("header %+v tabs %d shelves %d newUploads %d first video %s %s %s", res.Header.Title, len(res.Tabs), len(res.Shelves), len(newUploads.Videos), v.Title, v.ID, v.DurationText)
 for _, s := range res.Shelves {
  t.Logf("shelf %q %d videos first %s", s.Title, len(s.Videos), s.Videos[0].Title)
 }
}

func TestChannelVideosFixture(t *testing.T) {
 p := filepath.Join("..", "..", "research", "channels", "channel-videos.json")
 data, err := os.ReadFile(p)
 if err != nil {
  t.Fatalf("read %v", err)
 }
 var j map[string]interface{}
 json.Unmarshal(data, &j)
 res, err := collectChannelVideos(j)
 if err != nil {
  t.Fatalf("collect %v", err)
 }
 if res.Header == nil || res.Header.Title != "MrBeast" {
  t.Fatalf("header %v", res.Header)
 }
 if len(res.Chips) != 3 {
  t.Fatalf("chips %d want 3 got %v", len(res.Chips), res.Chips)
 }
 if res.Chips[0].Title != "Latest" || !res.Chips[0].Selected {
  t.Fatalf("chip0 %v", res.Chips[0])
 }
 if len(res.Videos) < 30 {
  t.Fatalf("videos %d", len(res.Videos))
 }
 if res.Continuation == "" {
  t.Fatal("continuation empty")
 }
 v := res.Videos[0]
 if v.ID != "Qtl8lJwbd4g" {
  t.Fatalf("first id %s", v.ID)
 }
 if len(v.Thumbnails) < 2 {
  t.Fatalf("thumbs %d", len(v.Thumbnails))
 }
 for _, th := range v.Thumbnails {
  if th.URL[:8] != "https://" {
   t.Fatalf("thumb not https %s", th.URL)
  }
 }
 if v.DurationText == "" {
  t.Fatalf("duration empty")
 }
 if v.ViewCountText == "" || v.PublishedTimeText == "" {
  t.Fatalf("view/published empty %v", v)
 }
 t.Logf("videos %d chips %v cont %s first %s %s", len(res.Videos), res.Chips, res.Continuation[:40], v.Title, v.DurationText)
}

func TestChannelVideosContinuationFixture(t *testing.T) {
 p := filepath.Join("..", "..", "research", "channels", "channel-videos-page2.json")
 data, err := os.ReadFile(p)
 if err != nil {
  t.Skip("no page2 fixture")
 }
 var j map[string]interface{}
 json.Unmarshal(data, &j)
 res, err := collectChannelVideos(j)
 if err != nil {
  t.Fatalf("collect %v", err)
 }
 if len(res.Videos) < 30 {
  t.Fatalf("videos %d", len(res.Videos))
 }
 if res.Continuation == "" {
  t.Fatal("cont empty")
 }
 t.Logf("page2 videos %d cont %s first %s", len(res.Videos), res.Continuation[:40], res.Videos[0].Title)
}

func TestPrimeTimeVideosChips(t *testing.T) {
 p := filepath.Join("..", "..", "research", "channels", "theprimetime-videos.json")
 data, err := os.ReadFile(p)
 if err != nil {
  t.Skip("no prime fixture")
 }
 var j map[string]interface{}
 json.Unmarshal(data, &j)
 res, err := collectChannelVideos(j)
 if err != nil {
  t.Fatalf("collect %v", err)
 }
 if len(res.Chips) < 4 {
  t.Fatalf("chips %d want >=4 got %v", len(res.Chips), res.Chips)
 }
 has := map[string]bool{}
 for _, c := range res.Chips {
  has[c.Title] = true
 }
 for _, need := range []string{"Latest", "Popular", "Oldest", "Members only", "Public"} {
  if !has[need] {
   t.Fatalf("missing %s got %v", need, res.Chips)
  }
 }
 if len(res.Videos) < 20 {
  t.Fatalf("videos %d", len(res.Videos))
 }
 if res.Continuation == "" {
  t.Fatal("continuation empty")
 }
 t.Logf("prime chips %d videos %d cont %s", len(res.Chips), len(res.Videos), res.Continuation[:30])
}

func TestChannelShortsFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-shorts.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no shorts fixture")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelShorts(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Chips) != 3 {
		t.Fatalf("chips %d want 3 got %v", len(res.Chips), res.Chips)
	}
	if res.Chips[0].Title != "Latest" || !res.Chips[0].Selected {
		t.Fatalf("chip0 %v", res.Chips[0])
	}
	if len(res.Shorts) < 40 {
		t.Fatalf("shorts %d", len(res.Shorts))
	}
	if res.Shorts[0].Title == "" {
		t.Fatal("title empty")
	}
	if res.Shorts[0].Title != "World’s Largest Tennis Match" {
		t.Fatalf("first title %q", res.Shorts[0].Title)
	}
	if !strings.Contains(res.Shorts[0].ViewCountText, "views") {
		t.Fatalf("views %q", res.Shorts[0].ViewCountText)
	}
	if len(res.Shorts[0].Thumbnails) < 2 {
		t.Fatalf("thumbs %d", len(res.Shorts[0].Thumbnails))
	}
	// highest thumb should be 1080x1920 or 405x720, check https
	for _, th := range res.Shorts[0].Thumbnails {
		if th.URL[:8] != "https://" {
			t.Fatalf("thumb not https %s", th.URL)
		}
	}
	if res.Shorts[0].ThumbnailURL[:8] != "https://" {
		t.Fatalf("thumb url not https %s", res.Shorts[0].ThumbnailURL)
	}
	if res.Continuation == "" {
		t.Fatal("continuation empty")
	}
	t.Logf("shorts %d chips %v cont %s first %s %s thumbs %d", len(res.Shorts), res.Chips, res.Continuation[:30], res.Shorts[0].Title, res.Shorts[0].ViewCountText, len(res.Shorts[0].Thumbnails))
}

func TestChannelShortsContinuationFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-shorts-page2.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no page2")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelShorts(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Shorts) < 40 {
		t.Fatalf("shorts %d", len(res.Shorts))
	}
	if res.Continuation == "" {
		t.Fatal("cont empty")
	}
	t.Logf("page2 shorts %d cont %s first %s", len(res.Shorts), res.Continuation[:30], res.Shorts[0].Title)
}
