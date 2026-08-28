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
	videos, _, shorts, topic, cont, est := collect(j)
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
	t.Logf("page1 videos %d shorts %d topic %s cont %s", len(videos), len(shorts), topic.Title, cont[:40])
}

func TestCollectPage2Fixture(t *testing.T) {
	p := filepath.Join("..", "..", "research", "search", "search-page2.json")
	data, _ := os.ReadFile(p)
	var j map[string]interface{}
	json.Unmarshal(data, &j)
	videos, _, shorts, _, cont, _ := collect(j)
	if len(videos) < 20 {
		t.Fatalf("videos %d", len(videos))
	}
	if cont == "" {
		t.Fatal("continuation empty")
	}
	if len(shorts) < 6 {
		t.Fatalf("shorts %d", len(shorts))
	}
	t.Logf("page2 videos %d shorts %d", len(videos), len(shorts))
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
