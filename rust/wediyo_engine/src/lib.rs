//! Wediyo metadata engine — Rust core
//!
//! This is the `metadata-only` phase. Full playback will be added later.
//! Exposed to Kotlin via UniFFI (proc-macro, no UDL).

use serde::{Deserialize, Serialize};
use thiserror::Error;

// UniFFI proc-macro scaffolding — generates scaffolding from exported items.
// Needs `build.rs` calling `uniffi::generate_scaffolding`.
uniffi::setup_scaffolding!("wediyo_engine");

/// Errors returned to Kotlin
#[derive(Debug, Error, uniffi::Error)]
pub enum WediyoError {
    #[error("network error: {msg}")]
    Network { msg: String },
    #[error("parse error: {msg}")]
    Parse { msg: String },
    #[error("not found: {id}")]
    NotFound { id: String },
    #[error("internal: {msg}")]
    Internal { msg: String },
}

/// Minimal video metadata — expand as needed
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct VideoMetadata {
    pub id: String,
    pub title: String,
    pub author: String,
    pub view_count: Option<u64>,
    pub duration_secs: Option<u64>,
    pub thumbnail_url: Option<String>,
}

/// Search result (metadata only)
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct SearchResult {
    pub query: String,
    pub videos: Vec<VideoMetadata>,
}

/// Simple hello for smoke-testing UniFFI wiring
#[uniffi::export]
pub fn hello_world() -> String {
    "hello from wediyo_engine 🦀".to_string()
}

/// Echo — useful for JNI round-trip tests
#[uniffi::export]
pub fn echo(input: String) -> String {
    input
}

/// Fetch video metadata (placeholder implementation)
/// Replace with real YouTube Innertube / scraping logic when ready.
#[uniffi::export]
pub fn get_video_metadata(video_id: String) -> Result<VideoMetadata, WediyoError> {
    if video_id.trim().is_empty() {
        return Err(WediyoError::Internal {
            msg: "video_id empty".into(),
        });
    }
    // TODO: real fetch — for now return stub so Kotlin can integrate
    Ok(VideoMetadata {
        id: video_id.clone(),
        title: format!("Placeholder title for {video_id}"),
        author: "Wediyo".into(),
        view_count: Some(0),
        duration_secs: Some(0),
        thumbnail_url: Some(format!("https://i.ytimg.com/vi/{video_id}/hqdefault.jpg")),
    })
}

/// Search stub — returns empty / placeholder
#[uniffi::export]
pub fn search(query: String) -> Result<SearchResult, WediyoError> {
    if query.trim().is_empty() {
        return Err(WediyoError::Internal {
            msg: "query empty".into(),
        });
    }
    Ok(SearchResult {
        query: query.clone(),
        videos: vec![VideoMetadata {
            id: "dQw4w9WgXcQ".into(),
            title: format!("Result for '{query}' (stub)"),
            author: "Wediyo stub".into(),
            view_count: Some(1_000_000),
            duration_secs: Some(213),
            thumbnail_url: Some("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg".into()),
        }],
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hello_works() {
        assert!(hello_world().contains("wediyo_engine"));
    }

    #[test]
    fn metadata_stub() {
        let m = get_video_metadata("abc123".into()).unwrap();
        assert_eq!(m.id, "abc123");
    }
}
