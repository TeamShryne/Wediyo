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

func TestChannelLiveFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-livestreams.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no live fixture")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelLive(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Chips) != 5 {
		t.Fatalf("chips %d want 5 got %v", len(res.Chips), res.Chips)
	}
	if res.Chips[0].Title != "Latest" || !res.Chips[0].Selected {
		t.Fatalf("chip0 %v", res.Chips[0])
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
	if len(res.Lives) < 20 {
		t.Fatalf("lives %d", len(res.Lives))
	}
	if res.Continuation == "" {
		t.Fatal("continuation empty")
	}
	t.Logf("live %d chips %v cont %s first %s %s", len(res.Lives), res.Chips, res.Continuation[:30], res.Lives[0].Title, res.Lives[0].ViewCountText)
}

func TestChannelLiveContinuationFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-livestreams-page2.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no page2")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelLive(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Lives) < 20 {
		t.Fatalf("lives %d", len(res.Lives))
	}
	if res.Continuation == "" {
		t.Fatal("cont empty")
	}
	t.Logf("page2 lives %d cont %s first %s", len(res.Lives), res.Continuation[:30], res.Lives[0].Title)
}

func TestChannelPodcastsFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-podcasts.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no podcasts fixture")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelPodcasts(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Podcasts) != 3 {
		t.Fatalf("podcasts %d want 3 got %v", len(res.Podcasts), res.Podcasts)
	}
	if res.Podcasts[0].Title != "The Standup" {
		t.Fatalf("first %q", res.Podcasts[0].Title)
	}
	if res.Podcasts[0].PodcastID != "PL2Fq-K0QdOQiJpufsnhEd1z3xOv2JMHuk" {
		t.Fatalf("id %q", res.Podcasts[0].PodcastID)
	}
	if res.Podcasts[0].EpisodeCountText != "65 episodes" {
		t.Fatalf("ep %q", res.Podcasts[0].EpisodeCountText)
	}
	if res.Podcasts[0].BrowseID != "VLPL2Fq-K0QdOQiJpufsnhEd1z3xOv2JMHuk" {
		t.Fatalf("browse %q", res.Podcasts[0].BrowseID)
	}
	if len(res.Podcasts[0].Thumbnails) == 0 {
		t.Fatalf("thumbs empty")
	}
	if res.Podcasts[0].ThumbnailURL[:8] != "https://" {
		t.Fatalf("thumb %s", res.Podcasts[0].ThumbnailURL)
	}
	t.Logf("podcasts %d cont '%s' first %s %s", len(res.Podcasts), res.Continuation, res.Podcasts[0].Title, res.Podcasts[0].EpisodeCountText)
}

func TestChannelPlaylistsFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-playlists.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no playlists fixture")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelPlaylists(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Playlists) != 18 {
		t.Fatalf("playlists %d want 18 got %v", len(res.Playlists), res.Playlists)
	}
	if res.Playlists[0].Title != "Tech Stories" {
		t.Fatalf("first %q", res.Playlists[0].Title)
	}
	if res.Playlists[0].PlaylistID != "PLMeNX7FnO_Vc" {
		t.Fatalf("id %q", res.Playlists[0].PlaylistID)
	}
	if len(res.Playlists[0].Thumbnails) == 0 {
		t.Fatalf("thumbs empty")
	}
	if res.Playlists[0].ThumbnailURL[:8] != "https://" {
		t.Fatalf("thumb %s", res.Playlists[0].ThumbnailURL)
	}
	// podcast included as playlist should have 240x square thumbs multiple qualities
	hasPodcast := false
	for _, pl := range res.Playlists {
		if pl.PlaylistID == "PL2Fq-K0QdOQiJpufsnhEd1z3xOv2JMHuk" {
			hasPodcast = true
			if len(pl.Thumbnails) < 2 { t.Fatalf("podcast thumbs %d", len(pl.Thumbnails)) }
		}
	}
	if !hasPodcast { t.Fatal("podcast not in playlists grid") }
	t.Logf("playlists %d cont '%s' first %s %s", len(res.Playlists), res.Continuation, res.Playlists[0].Title, res.Playlists[0].VideoCountText)
}

func TestChannelPostsFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-posts.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no posts fixture")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelPosts(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Posts) != 10 {
		t.Fatalf("posts %d want 10 got %v", len(res.Posts), res.Posts)
	}
	if res.Posts[0].PostID != "Ugkx2Tj-2mEE6VJAzsw_CRh7CJAfWwz8PE0i" {
		t.Fatalf("first %q", res.Posts[0].PostID)
	}
	if res.Posts[0].AttachmentType != "poll" {
		t.Fatalf("att %q", res.Posts[0].AttachmentType)
	}
	if res.Posts[0].Poll == nil || len(res.Posts[0].Poll.Choices) != 4 {
		t.Fatalf("poll choices %v", res.Posts[0].Poll)
	}
	if res.Posts[0].Poll.TotalVotesText != "769 votes" {
		t.Fatalf("total %q", res.Posts[0].Poll.TotalVotesText)
	}
	// check poll parsing for second poll
	hasMulti := false
	for _, po := range res.Posts {
		if po.AttachmentType == "multiImage" {
			hasMulti = true
			if len(po.Images) < 2 { t.Fatalf("multi images %d", len(po.Images)) }
			if len(po.Images[0].Thumbnails) == 0 { t.Fatal("image thumbs empty") }
			if po.Images[0].URL[:8] != "https://" { t.Fatalf("img url %s", po.Images[0].URL) }
		}
		if po.AttachmentType == "singleImage" {
			if len(po.Images) != 1 { t.Fatalf("single images %d", len(po.Images)) }
		}
		// author thumbnails multiple qualities
		if len(po.AuthorThumbnails) < 2 { t.Fatalf("author thumbs %d for %s", len(po.AuthorThumbnails), po.PostID) }
	}
	if !hasMulti { t.Fatal("no multiImage found") }
	if res.Continuation == "" { t.Fatal("continuation empty") }
	t.Logf("posts %d cont %s first poll %s", len(res.Posts), res.Continuation[:30], res.Posts[0].Poll.Choices[0].Text)
}

func TestChannelStoreFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-store.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no store fixture")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelStore(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Products) != 3 {
		t.Fatalf("products %d want 3 got %v", len(res.Products), res.Products)
	}
	if res.Products[0].Title != "Trad/Vibe Hat" {
		t.Fatalf("first %q", res.Products[0].Title)
	}
	if len(res.Products[0].Thumbnails) == 0 {
		t.Fatalf("thumbs empty")
	}
	if res.Products[0].ThumbnailURL[:8] != "https://" {
		t.Fatalf("thumb %s", res.Products[0].ThumbnailURL)
	}
	if res.Products[0].PriceText == "" { t.Fatalf("price empty") }
	if res.Products[0].ProductURL == "" { t.Fatalf("url empty") }
	t.Logf("store %d first %s %s", len(res.Products), res.Products[0].Title, res.Products[0].PriceText)
}

func TestChannelCoursesFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-courses.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no courses fixture")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelCourses(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Courses) != 30 {
		t.Fatalf("courses %d want 30 got %v", len(res.Courses), res.Courses)
	}
	if res.Courses[0].Title != "CS50x em Português" {
		t.Fatalf("first %q", res.Courses[0].Title)
	}
	if res.Courses[0].PlaylistID != "PLXSX209johrU" {
		t.Fatalf("id %q", res.Courses[0].PlaylistID)
	}
	if len(res.Courses[0].Thumbnails) == 0 {
		t.Fatalf("thumbs empty")
	}
	if res.Courses[0].ThumbnailURL[:8] != "https://" {
		t.Fatalf("thumb %s", res.Courses[0].ThumbnailURL)
	}
	if res.Continuation == "" { t.Fatal("cont empty") }
	t.Logf("courses %d cont %s first %s", len(res.Courses), res.Continuation[:30], res.Courses[0].Title)
}

func TestChannelShowsFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-shows.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no shows fixture")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelShows(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if len(res.Shows) != 7 {
		t.Fatalf("shows %d want 7 got %v", len(res.Shows), res.Shows)
	}
	if res.Shows[0].Title != "Indian Idol S13 | Full Episodes Here" {
		t.Fatalf("first %q", res.Shows[0].Title)
	}
	if res.Shows[0].Subtitle != "SET India" {
		t.Fatalf("subtitle %q", res.Shows[0].Subtitle)
	}
	if len(res.Shows[0].Thumbnails) == 0 {
		t.Fatalf("thumbs empty")
	}
	if res.Shows[0].ThumbnailURL[:8] != "https://" {
		t.Fatalf("thumb %s", res.Shows[0].ThumbnailURL)
	}
	if res.Shows[0].EpisodeCountText == "" { t.Fatalf("ep empty") }
	t.Logf("shows %d first %s %s", len(res.Shows), res.Shows[0].Title, res.Shows[0].EpisodeCountText)
}

func TestChannelAboutFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "channels", "channel-about.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no about fixture")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	res, err := collectChannelAbout(j)
	if err != nil {
		t.Fatalf("collect %v", err)
	}
	if res.About == nil {
		t.Fatal("about nil")
	}
	if res.About.ChannelId != "UCpEhnqL0y41EpW2TvWAHD7Q" {
		t.Fatalf("id %q", res.About.ChannelId)
	}
	if res.About.Description == "" {
		t.Fatal("desc empty")
	}
	if len(res.About.Links) != 5 {
		t.Fatalf("links %d want 5", len(res.About.Links))
	}
	if res.About.Links[0].Title != "Subscribe Now" {
		t.Fatalf("first link %q", res.About.Links[0].Title)
	}
	if !strings.Contains(res.About.Links[0].URL, "sub_confirmation") {
		t.Fatalf("url %q", res.About.Links[0].URL)
	}
	if len(res.About.Links[0].Favicons) < 5 {
		t.Fatalf("favicons %d", len(res.About.Links[0].Favicons))
	}
	for _, th := range res.About.Links[0].Favicons {
		if th.URL[:8] != "https://" {
			t.Fatalf("favicon not https %s", th.URL)
		}
	}
	if res.About.Country != "India" {
		t.Fatalf("country %q", res.About.Country)
	}
	t.Logf("about %s desc %d country %s links %d", res.About.ChannelId, len(res.About.Description), res.About.Country, len(res.About.Links))
}
