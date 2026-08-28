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
func (r *SearchResult) FilterGroupsJSON() string {
	b, _ := json.Marshal(r.FilterGroups)
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

func (h *ChannelHeader) AvatarsJSON() string {
	b, _ := json.Marshal(h.Avatars)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (h *ChannelHeader) BannersJSON() string {
	b, _ := json.Marshal(h.Banners)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (t *ChannelTab) ToJSON() string {
	b, _ := json.Marshal(t)
	return string(b)
}
func (s *ChannelShelf) VideosJSON() string {
	b, _ := json.Marshal(s.Videos)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelHomeResult) TabsJSON() string {
	b, _ := json.Marshal(r.Tabs)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelHomeResult) ShelvesJSON() string {
	b, _ := json.Marshal(r.Shelves)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelHomeResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}

func (r *ChannelVideosResult) TabsJSON() string {
	b, _ := json.Marshal(r.Tabs)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelVideosResult) ChipsJSON() string {
	b, _ := json.Marshal(r.Chips)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelVideosResult) VideosJSON() string {
	b, _ := json.Marshal(r.Videos)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelVideosResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}

func (r *ChannelShortsResult) ShortsJSON() string {
	b, _ := json.Marshal(r.Shorts)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelShortsResult) TabsJSON() string {
	b, _ := json.Marshal(r.Tabs)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelShortsResult) ChipsJSON() string {
	b, _ := json.Marshal(r.Chips)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelShortsResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}

func (r *ChannelLiveResult) ChipsJSON() string {
	b, _ := json.Marshal(r.Chips)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelLiveResult) LivesJSON() string {
	b, _ := json.Marshal(r.Lives)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelLiveResult) TabsJSON() string {
	b, _ := json.Marshal(r.Tabs)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelLiveResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}

func (p *ChannelPodcast) ThumbnailsJSON() string {
	b, _ := json.Marshal(p.Thumbnails)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelPodcastsResult) PodcastsJSON() string {
	b, _ := json.Marshal(r.Podcasts)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelPodcastsResult) TabsJSON() string {
	b, _ := json.Marshal(r.Tabs)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelPodcastsResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}

func (p *ChannelPlaylist) ThumbnailsJSON() string {
	b, _ := json.Marshal(p.Thumbnails)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelPlaylistsResult) PlaylistsJSON() string {
	b, _ := json.Marshal(r.Playlists)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelPlaylistsResult) TabsJSON() string {
	b, _ := json.Marshal(r.Tabs)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelPlaylistsResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}

func (p *ChannelPost) AuthorThumbnailsJSON() string {
	b, _ := json.Marshal(p.AuthorThumbnails)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (p *ChannelPost) ImagesJSON() string {
	b, _ := json.Marshal(p.Images)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (p *ChannelPostPoll) ToJSON() string {
	b, _ := json.Marshal(p)
	return string(b)
}
func (r *ChannelPostsResult) PostsJSON() string {
	b, _ := json.Marshal(r.Posts)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelPostsResult) TabsJSON() string {
	b, _ := json.Marshal(r.Tabs)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelPostsResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}

func (p *ChannelStoreProduct) ThumbnailsJSON() string {
	b, _ := json.Marshal(p.Thumbnails)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelStoreResult) ProductsJSON() string {
	b, _ := json.Marshal(r.Products)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelStoreResult) TabsJSON() string {
	b, _ := json.Marshal(r.Tabs)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelStoreResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}

func (c *ChannelCourse) ThumbnailsJSON() string {
	b, _ := json.Marshal(c.Thumbnails)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelCoursesResult) CoursesJSON() string {
	b, _ := json.Marshal(r.Courses)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelCoursesResult) TabsJSON() string {
	b, _ := json.Marshal(r.Tabs)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelCoursesResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}

func (s *ChannelShow) ThumbnailsJSON() string {
	b, _ := json.Marshal(s.Thumbnails)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelShowsResult) ShowsJSON() string {
	b, _ := json.Marshal(r.Shows)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelShowsResult) TabsJSON() string {
	b, _ := json.Marshal(r.Tabs)
	if string(b) == "null" {
		return "[]"
	}
	return string(b)
}
func (r *ChannelShowsResult) ToJSON() string {
	b, _ := json.Marshal(r)
	return string(b)
}
