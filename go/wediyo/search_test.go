package wediyo

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func TestParseVideoRendererFixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "search", "search-response.json")
	data, err := os.ReadFile(p)
	if err != nil {
		t.Fatalf("read fixture: %v", err)
	}
	var j map[string]interface{}
	if err := json.Unmarshal(data, &j); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	contents := j["contents"].(map[string]interface{})["twoColumnSearchResultsRenderer"].(map[string]interface{})["primaryContents"].(map[string]interface{})["sectionListRenderer"].(map[string]interface{})["contents"].([]interface{})
	sec := contents[0].(map[string]interface{})["itemSectionRenderer"].(map[string]interface{})["contents"].([]interface{})
	vr := sec[1].(map[string]interface{})["videoRenderer"]
	vm := parseVideoRenderer(vr)
	if vm == nil || vm.ID != "n61ULEU7CO0" {
		t.Fatalf("parse video failed %+v", vm)
	}
	if vm.ChannelID != "UCSJ4gkVC6NrvII8umztf0Ow" {
		t.Fatalf("channel id wrong %v", vm.ChannelID)
	}
	if vm.ViewCount == 0 {
		t.Fatalf("viewCount 0")
	}
}

func TestCollectPage1Fixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "search", "search-response.json")
	data, _ := os.ReadFile(p)
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	videos, _, shorts, playlists, topic, chips, cont, est := collect(j)
	if len(videos) < 19 {
		t.Fatalf("videos %d", len(videos))
	}
	if cont == "" {
		t.Fatal("continuation empty")
	}
	if est == "" {
		t.Fatal("estimated empty")
	}
	if topic == nil {
		t.Fatal("topicCard nil for lo-fi beats")
	}
	if len(shorts) < 6 {
		t.Fatalf("shorts %d", len(shorts))
	}
	if len(playlists) != 0 {
		t.Logf("unexpected playlists %d", len(playlists))
	}
	if len(chips) != 5 { // All, Shorts, Videos, Recently uploaded, Live (Watched/Unwatched filtered)
		t.Fatalf("chips %d expected 5, got %v", len(chips), chips)
	}
	for _, c := range chips {
		if c.Title == "Watched" || c.Title == "Unwatched" {
			t.Fatalf("unexpected filtered chip %s", c.Title)
		}
	}
	t.Logf("page1 videos %d shorts %d playlists %d topic %s chips %v cont %s", len(videos), len(shorts), len(playlists), topic.Title, chips, cont[:40])
	if len(videos) > 0 && len(videos[0].Thumbnails) == 0 {
		t.Fatalf("thumbnails missing")
	}
}

func TestCollectPage2Fixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "search", "search-page2.json")
	data, _ := os.ReadFile(p)
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	videos, _, shorts, playlists, _, _, cont, _ := collect(j)
	if len(videos) < 20 {
		t.Fatalf("videos %d", len(videos))
	}
	if cont == "" {
		t.Fatal("continuation empty")
	}
	if len(shorts) < 6 {
		t.Fatalf("shorts %d", len(shorts))
	}
	t.Logf("page2 videos %d shorts %d playlists %d", len(videos), len(shorts), len(playlists))
}

func TestCollectClass10Playlist(t *testing.T) {
	p := "/tmp/class10.json"
	data, err := os.ReadFile(p)
	if err != nil {
		t.Skip("no /tmp/class10.json — run bash fetch first")
	}
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	videos, _, shorts, playlists, _, _, cont, _ := collect(j)
	if len(playlists) < 15 {
		t.Fatalf("playlists %d, expected >=15 for class 10 playlist", len(playlists))
	}
	if len(playlists) > 0 && playlists[0].Thumbnails[0].URL == "" {
		t.Fatalf("playlist thumbnails missing")
	}
	// check course badge
	hasCourse := false
	for _, pl := range playlists {
		if pl.IsCourse {
			hasCourse = true
			break
		}
	}
	if !hasCourse {
		t.Fatalf("no course found in playlists")
	}
	t.Logf("class10 playlists %d videos %d shorts %d cont %s sample %s (%s) thumbnails %d", len(playlists), len(videos), len(shorts), cont[:30], playlists[0].Title, playlists[0].VideoCountText, len(playlists[0].Thumbnails))
}

func TestLiveSearch(t *testing.T) {
	if testing.Short() {
		t.Skip()
	}
	s, err := FetchInnertubeSession()
	if err != nil {
		t.Fatalf("session: %v", err)
	}
	r1, err := Search(s, "lo-fi beats", "")
	if err != nil {
		t.Fatalf("search: %v", err)
	}
	if len(r1.Videos) < 5 {
		t.Fatalf("videos %d", len(r1.Videos))
	}
	if r1.Continuation == "" {
		t.Fatal("no continuation")
	}
	t.Logf("live page1 videos %d channels %d shorts %d topic %v", len(r1.Videos), len(r1.Channels), len(r1.Shorts), r1.TopicCard)
	r2, err := Search(s, "lo-fi beats", r1.Continuation)
	if err != nil {
		t.Fatalf("search page2: %v", err)
	}
	if len(r2.Videos) < 5 {
		t.Fatalf("page2 videos %d", len(r2.Videos))
	}
	if r1.Videos[0].ID == r2.Videos[0].ID {
		t.Fatal("pagination returned same first video")
	}
	// MrBeast should have channel
	r3, _ := Search(s, "MrBeast", "")
	if len(r3.Channels) == 0 {
		t.Fatalf("MrBeast channels 0, expected channelRenderer")
	}
	t.Logf("MrBeast channels %d", len(r3.Channels))
}
