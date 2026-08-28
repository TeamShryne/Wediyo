package wediyo

import "encoding/json"

// Java-accessible JSON helpers for fields that gomobile skips (slices of struct/string)
// Kotlin can call these and parse JSON via org.json / kotlinx.serialization.

func (v *VideoMetadata) ThumbnailsJSON() string {
	b, _ := json.Marshal(v.Thumbnails)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (v *VideoMetadata) ChannelAvatarsJSON() string {
	b, _ := json.Marshal(v.ChannelAvatars)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (v *VideoMetadata) BadgesJSON() string {
	b, _ := json.Marshal(v.Badges)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}

func (c *ChannelResult) ThumbnailsJSON() string {
	b, _ := json.Marshal(c.Thumbnails)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (c *ChannelResult) BadgesJSON() string {
	b, _ := json.Marshal(c.Badges)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}

func (s *ShortResult) ThumbnailsJSON() string {
	b, _ := json.Marshal(s.Thumbnails)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}

func (t *TopicCard) AvatarsJSON() string {
	b, _ := json.Marshal(t.Avatars)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}

func (r *SearchResult) VideosJSON() string {
	b, _ := json.Marshal(r.Videos)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *SearchResult) ChannelsJSON() string {
	b, _ := json.Marshal(r.Channels)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *SearchResult) ShortsJSON() string {
	b, _ := json.Marshal(r.Shorts)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *SearchResult) PlaylistsJSON() string {
	b, _ := json.Marshal(r.Playlists)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *SearchResult) ChipsJSON() string {
	b, _ := json.Marshal(r.Chips)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (p *PlaylistResult) ThumbnailsJSON() string {
	b, _ := json.Marshal(p.Thumbnails)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (p *PlaylistResult) BadgesJSON() string {
	b, _ := json.Marshal(p.Badges)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *SearchResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}
