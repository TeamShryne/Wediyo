package wediyo

type Thumbnail struct {
	URL    string `json:"url"`
	Width  int    `json:"width"`
	Height int    `json:"height"`
}

// VideoMetadata — exhaustive for search results
type VideoMetadata struct {
	ID                 string      `json:"id"`
	Title              string      `json:"title"`
	Author             string      `json:"author"`
	ViewCount          int64       `json:"view_count"`          // 0 if not parsed
	ViewCountText      string      `json:"view_count_text"`     // e.g. "56,786,655 views"
	ShortViewCountText string      `json:"short_view_count_text"` // "56M views"
	PublishedTimeText  string      `json:"published_time_text"` // "4 years ago"
	DurationText       string      `json:"duration_text"`       // "6:10:58"
	DurationSecs       int64       `json:"duration_secs"`       // 0 if LIVE
	ThumbnailURL       string      `json:"thumbnail_url"`       // highest res convenience
	Thumbnails         []Thumbnail `json:"thumbnails"`          // all qualities 360p/720p etc
	ChannelID          string      `json:"channel_id"`
	ChannelAvatarURL   string      `json:"channel_avatar_url"`
	ChannelAvatars     []Thumbnail `json:"channel_avatars"` // s88/s176
	IsLive             bool        `json:"is_live"`
	Badges             []string    `json:"badges"`
	DescriptionSnippet string      `json:"description_snippet"`
}

type ChannelResult struct {
	ChannelID           string      `json:"channel_id"`
	Title               string      `json:"title"`
	Handle              string      `json:"handle"`               // "@MrBeast"
	SubscriberCountText string      `json:"subscriber_count_text"` // "514M subscribers"
	ThumbnailURL        string      `json:"thumbnail_url"`        // highest
	Thumbnails          []Thumbnail `json:"thumbnails"`           // s88/s176
	DescriptionSnippet  string      `json:"description_snippet"`
	Verified            bool        `json:"verified"`
	Badges              []string    `json:"badges"`
}

type ShortResult struct {
	VideoID            string      `json:"video_id"`
	Title              string      `json:"title"`
	ThumbnailURL       string      `json:"thumbnail_url"` // highest
	Thumbnails         []Thumbnail `json:"thumbnails"`    // 1080x1920 frame0
	ViewCountText      string      `json:"view_count_text"`
	AccessibilityLabel string      `json:"accessibility_label"`
}

type TopicCard struct {
	Title               string      `json:"title"`
	BrowseID            string      `json:"browse_id"`
	AvatarURL           string      `json:"avatar_url"` // highest
	Avatars             []Thumbnail `json:"avatars"`    // s176 etc
	Handle              string      `json:"handle"`               // "@WRLDMusic" etc
	SubscriberCountText string      `json:"subscriber_count_text"`// "6.77K subscribers"
	VideoCountText      string      `json:"video_count_text"`     // "1 video"
	Verified            bool        `json:"verified"`
}

type PlaylistResult struct {
	PlaylistID       string      `json:"playlist_id"` // PLx...
	Title            string      `json:"title"`
	ChannelName      string      `json:"channel_name"`
	ChannelID        string      `json:"channel_id"`
	ThumbnailURL     string      `json:"thumbnail_url"`
	Thumbnails       []Thumbnail `json:"thumbnails"`
	VideoCountText   string      `json:"video_count_text"` // "24 lessons" or "50 videos"
	VideoCount       int         `json:"video_count"`
	IsCourse         bool        `json:"is_course"` // badge COURSE
	Badges           []string    `json:"badges"`
	DescriptionSnippet string    `json:"description_snippet"` // first video title
}

type FilterChip struct {
	Title    string `json:"title"`    // e.g. "All", "Shorts", "Videos", "Live"
	Selected bool   `json:"selected"`
	Token    string `json:"token"` // continuation token for this filter; empty for selected All
}

type SearchFilter struct {
	Label  string `json:"label"`  // e.g. "Videos", "Under 3 minutes", "Live"
	Params string `json:"params"` // sp value (base64, url decoded e.g. EgIQAQ==)
}

type SearchFilterGroup struct {
	Title   string         `json:"title"` // TYPE, DURATION, UPLOAD DATE, FEATURES, PRIORITIZE
	Filters []SearchFilter `json:"filters"`
}

type SearchResult struct {
	Query            string              `json:"query"`
	Videos           []VideoMetadata     `json:"videos"`
	Channels         []ChannelResult     `json:"channels"`
	Shorts           []ShortResult       `json:"shorts"`
	Playlists        []PlaylistResult    `json:"playlists"`
	TopicCard        *TopicCard          `json:"topic_card,omitempty"`
	Chips            []FilterChip        `json:"chips"`
	FilterGroups     []SearchFilterGroup `json:"filter_groups"`
	Continuation     string              `json:"continuation"`      // empty if none
	EstimatedResults string              `json:"estimated_results"` // e.g. "2494772"
}

// Channel home types — browse channel/featured
type ChannelHeader struct {
	ChannelID           string      `json:"channel_id"`
	Title               string      `json:"title"`
	Handle              string      `json:"handle"` // "@MrBeast"
	AvatarURL           string      `json:"avatar_url"` // highest
	Avatars             []Thumbnail `json:"avatars"`    // s72/s120/s160 etc
	BannerURL           string      `json:"banner_url"` // highest
	Banners             []Thumbnail `json:"banners"`    // 1060/1138/.../2560
	SubscriberCountText string      `json:"subscriber_count_text"` // "514M subscribers"
	VideoCountText      string      `json:"video_count_text"`      // "999 videos"
	Description         string      `json:"description"`
	Verified            bool        `json:"verified"`
	ChannelURL          string      `json:"channel_url"` // https://youtube.com/channel/UC...
	RSSUrl              string      `json:"rss_url"`
	Keywords            string      `json:"keywords"`
}

type ChannelTab struct {
	Title            string `json:"title"`              // Home, Videos, Shorts, Playlists, Posts
	Selected         bool   `json:"selected"`
	Params           string `json:"params"`             // url-unescaped, e.g. EghmZWF0dXJlZ...
	BrowseID         string `json:"browse_id"`          // UC...
	CanonicalBaseURL string `json:"canonical_base_url"` // /@MrBeast
}

type ChannelShelf struct {
	Title    string          `json:"title"`     // "New Uploads", "For You", etc
	BrowseID string          `json:"browse_id"` // VLPL... for playlist shelves
	Videos   []VideoMetadata `json:"videos"`
}

type ChannelHomeResult struct {
	Header  *ChannelHeader `json:"header,omitempty"`
	Tabs    []ChannelTab   `json:"tabs"`
	Shelves []ChannelShelf `json:"shelves"`
}

type ChannelVideoChip struct {
	Title    string `json:"title"`    // Latest, Popular, Oldest
	Selected bool   `json:"selected"`
	Token    string `json:"token"` // continuation token for reload
}

type ChannelVideosResult struct {
	Header       *ChannelHeader     `json:"header,omitempty"`
	Tabs         []ChannelTab       `json:"tabs"`
	Chips        []ChannelVideoChip `json:"chips"` // filter chips for videos
	Videos       []VideoMetadata    `json:"videos"`
	Continuation string             `json:"continuation"` // next page token
}

type ChannelShortsResult struct {
	Header       *ChannelHeader     `json:"header,omitempty"`
	Tabs         []ChannelTab       `json:"tabs"`
	Chips        []ChannelVideoChip `json:"chips"` // Latest/Popular/Oldest same as videos
	Shorts       []ShortResult      `json:"shorts"`
	Continuation string             `json:"continuation"` // next page token
}

type ChannelLiveResult struct {
	Header       *ChannelHeader     `json:"header,omitempty"`
	Tabs         []ChannelTab       `json:"tabs"`
	Chips        []ChannelVideoChip `json:"chips"` // Latest (dropdown) + Popular/Oldest + Members only/Public
	Lives        []VideoMetadata    `json:"lives"` // live streams as VideoMetadata (thumbs, title, badges, stream time)
	Continuation string             `json:"continuation"`
}

type ChannelPodcast struct {
	PodcastID        string      `json:"podcast_id"` // PL...
	BrowseID         string      `json:"browse_id"`  // VL...
	Title            string      `json:"title"`
	ThumbnailURL     string      `json:"thumbnail_url"`
	Thumbnails       []Thumbnail `json:"thumbnails"`
	EpisodeCountText string      `json:"episode_count_text"` // 65 episodes
	EpisodeCount     int         `json:"episode_count"`
	UpdatedText      string      `json:"updated_text"` // Updated today
}

type ChannelPodcastsResult struct {
	Header       *ChannelHeader   `json:"header,omitempty"`
	Tabs         []ChannelTab     `json:"tabs"`
	Podcasts     []ChannelPodcast `json:"podcasts"`
	Continuation string           `json:"continuation"`
}

type ChannelPlaylist struct {
	PlaylistID     string      `json:"playlist_id"` // PL...
	BrowseID       string      `json:"browse_id"`   // VL...
	Title          string      `json:"title"`
	ThumbnailURL   string      `json:"thumbnail_url"`
	Thumbnails     []Thumbnail `json:"thumbnails"` // all qualities 480x270 etc
	VideoCountText string      `json:"video_count_text"` // "2 videos"
	VideoCount     int         `json:"video_count"`
}

type ChannelPlaylistsResult struct {
	Header       *ChannelHeader    `json:"header,omitempty"`
	Tabs         []ChannelTab      `json:"tabs"`
	Playlists    []ChannelPlaylist `json:"playlists"`
	Continuation string            `json:"continuation"`
}

type ChannelPostPollChoice struct {
	Text string `json:"text"`
}

type ChannelPostPoll struct {
	Choices        []ChannelPostPollChoice `json:"choices"`
	TotalVotesText string                  `json:"total_votes_text"` // "769 votes"
	Type           string                  `json:"type"`             // POLL_TYPE_TEXT
}

type ChannelPostImage struct {
	URL        string      `json:"url"`
	Thumbnails []Thumbnail `json:"thumbnails"`
}

type ChannelPost struct {
	PostID            string            `json:"post_id"`
	AuthorText        string            `json:"author_text"`
	AuthorThumbnailURL string           `json:"author_thumbnail_url"`
	AuthorThumbnails  []Thumbnail       `json:"author_thumbnails"`
	ContentText       string            `json:"content_text"`
	PublishedTimeText string            `json:"published_time_text"`
	VoteCountText     string            `json:"vote_count_text"` // "23" / "1.8K"
	VoteCount         string            `json:"vote_count_label"`// accessibility label
	AttachmentType    string            `json:"attachment_type"` // poll | singleImage | multiImage | video | none
	Poll              *ChannelPostPoll  `json:"poll,omitempty"`
	Images            []ChannelPostImage `json:"images,omitempty"`
	Video             *VideoMetadata    `json:"video,omitempty"`
}

type ChannelPostsResult struct {
	Header       *ChannelHeader `json:"header,omitempty"`
	Tabs         []ChannelTab   `json:"tabs"`
	Posts        []ChannelPost  `json:"posts"`
	Continuation string         `json:"continuation"`
}

type ChannelStoreProduct struct {
	Title            string      `json:"title"`
	ThumbnailURL     string      `json:"thumbnail_url"`
	Thumbnails       []Thumbnail `json:"thumbnails"`
	PriceText        string      `json:"price_text"` // "₹2,767.40"
	MerchantName     string      `json:"merchant_name"`
	FromText         string      `json:"from_text"` // "From ThePrimeagen"
	ProductURL       string      `json:"product_url"` // https://...
}

type ChannelStoreResult struct {
	Header       *ChannelHeader        `json:"header,omitempty"`
	Tabs         []ChannelTab          `json:"tabs"`
	Products     []ChannelStoreProduct `json:"products"`
	Continuation string                `json:"continuation"`
}

type ChannelCourse struct {
	PlaylistID     string      `json:"playlist_id"` // PL...
	BrowseID       string      `json:"browse_id"`   // VL...
	Title          string      `json:"title"`
	ThumbnailURL   string      `json:"thumbnail_url"`
	Thumbnails     []Thumbnail `json:"thumbnails"` // main 480x270 + stacked preview 43x20? main only for now
	VideoCountText string      `json:"video_count_text"` // "13"
	VideoCount     int         `json:"video_count"`
}

type ChannelCoursesResult struct {
	Header       *ChannelHeader  `json:"header,omitempty"`
	Tabs         []ChannelTab    `json:"tabs"`
	Courses      []ChannelCourse `json:"courses"`
	Continuation string          `json:"continuation"`
}

type ChannelShow struct {
	ShowID           string      `json:"show_id"`   // VLPL...
	BrowseID         string      `json:"browse_id"` // same
	Title            string      `json:"title"`
	ThumbnailURL     string      `json:"thumbnail_url"`
	Thumbnails       []Thumbnail `json:"thumbnails"`
	Subtitle         string      `json:"subtitle"` // "SET India"
	EpisodeCountText string      `json:"episode_count_text"` // "57 episodes"
	EpisodeCount     int         `json:"episode_count"`
}

type ChannelShowsResult struct {
	Header       *ChannelHeader `json:"header,omitempty"`
	Tabs         []ChannelTab   `json:"tabs"`
	Shows        []ChannelShow  `json:"shows"`
	Continuation string         `json:"continuation"`
}

type ChannelAboutLink struct {
	Title       string      `json:"title"` // e.g. "Subscribe Now"
	URL         string      `json:"url"`   // resolved https://...
	LinkText    string      `json:"link_text"` // e.g. "youtube.com/c/setindia..."
	FaviconURL  string      `json:"favicon_url"` // highest
	Favicons    []Thumbnail `json:"favicons"` // 16..256
}

type ChannelAbout struct {
	Description                string             `json:"description"`
	Country                    string             `json:"country"`
	SubscriberCountText        string             `json:"subscriber_count_text"` // "190M subscribers"
	ViewCountText              string             `json:"view_count_text"`       // "191,694,059,731 views"
	JoinedDateText             string             `json:"joined_date_text"`      // "Joined Sep 20, 2006"
	CanonicalChannelUrl        string             `json:"canonical_channel_url"` // "http://www.youtube.com/@SETIndia"
	DisplayCanonicalChannelUrl string             `json:"display_canonical_channel_url"` // "www.youtube.com/@SETIndia"
	ChannelId                  string             `json:"channel_id"`
	VideoCountText             string             `json:"video_count_text"` // "175,115 videos"
	VideoCount                 int                `json:"video_count"`
	Links                      []ChannelAboutLink `json:"links"`
}

type ChannelAboutResult struct {
	Header *ChannelHeader `json:"header,omitempty"`
	Tabs   []ChannelTab   `json:"tabs"`
	About  *ChannelAbout  `json:"about,omitempty"`
}

// InnertubeSession — from GET youtube.com Set-Cookie + ytcfg (stateless: caller holds)
type InnertubeSession struct {
	VisitorData            string `json:"visitor_data"`
	APIKey                 string `json:"api_key"`
	ClientVersion          string `json:"client_version"`
	ClientName             string `json:"client_name"`
	YSC                    string `json:"ysc"`
	VisitorInfoLive        string `json:"visitor_info_live"`
	SecureYNID             string `json:"secure_ynid"`
	RolloutToken           string `json:"rollout_token"`
	VisitorPrivacyMetadata string `json:"visitor_privacy_metadata"`
	Pref                   string `json:"pref"`
	GPS                    string `json:"gps"`
	CookieHeader           string `json:"cookie_header"`
}
