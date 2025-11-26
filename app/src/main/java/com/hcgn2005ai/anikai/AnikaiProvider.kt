package com.lagradost.cloudstream3.providers

import app.cloudstream3.API
import app.cloudstream3.LoadResponse
import app.cloudstream3.MainAPI
import app.cloudstream3.TvType
import app.cloudstream3.SubtitleFile
import app.cloudstream3.HomePageList
import app.cloudstream3.SearchResponse
import app.cloudstream3.Episode
import app.cloudstream3.extractors.ExtractorApi // Correct import for ExtractorApi
import app.cloudstream3.utils.ExtractorLink
import app.cloudstream3.utils.Qualities
import app.cloudstream3.utils.get
import org.jsoup.Jsoup

class AnikaiProvider : MainAPI() {
    override val name = "Anikai"
    override val mainUrl = "https://anikai.to/"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val lang = "en"
    override val author = "hcgn2005-ai"

    // Define the categories that will appear on the main page.
    // Each pair is a display name and the URL for that category.
    override val mainPage = mainPageOf(
        "Trending Anime" to "${mainUrl}/trending-anime",
        "Recently Updated" to "${mainUrl}/recently-updated",
        "Latest Releases" to "${mainUrl}/latest",
        "Top Airing" to "${mainUrl}/top-airing",
        "Popular Movies" to "${mainUrl}/popular-movies"
    )

    // Loads the main page content for a given category (request.name).
    override suspend fun loadMainPage(page: Int, request: HomePageList): HomePageList? {
        // Construct the URL based on the requested category.
        // Adjust these URLs if Anikai uses different paths for categories.
        val url = when (request.name) {
            "Trending Anime" -> "${mainUrl}/trending-anime"
            "Recently Updated" -> "${mainUrl}/recently-updated"
            "Latest Releases" -> "${mainUrl}/latest"
            "Top Airing" -> "${mainUrl}/top-airing"
            "Popular Movies" -> "${mainUrl}/popular-movies"
            else -> "${mainUrl}/latest" // Fallback to latest releases
        }

        // Fetch the HTML content from the constructed URL and parse it with Jsoup.
        val document = get(url).parsed<String>().let { Jsoup.parse(it) }

        // Select items from the page using CSS selectors.
        // This selector targets individual anime/movie cards in a list.
        // --- ADJUST CSS SELECTOR HERE IF SITE STRUCTURE CHANGES ---
        val items = document.select("div.film_list-wrap div.flw-item").mapNotNull { element ->
            // Extract the title.
            // --- ADJUST CSS SELECTOR HERE ---
            val title = element.selectFirst(".film-name a")?.text() ?: return@mapNotNull null

            // Extract the relative URL for the item's detail page.
            // --- ADJUST CSS SELECTOR HERE ---
            val href = element.selectFirst(".film-name a")?.attr("href") ?: return@mapNotNull null

            // Extract the poster image URL. Many sites use 'data-src' for lazy loading,
            // so we try that first, then fall back to 'src'.
            // --- ADJUST CSS SELECTOR HERE ---
            val posterUrl = element.selectFirst(".film-poster img")?.attr("data-src")
                ?: element.selectFirst(".film-poster img")?.attr("src")

            // Create a new SearchResponse object for CloudStream.
            newAnimeSearchResponse(title, "$mainUrl$href") {
                this.posterUrl = posterUrl
                // Additional details like quality, year, or type could be extracted here if available
                // in the list view elements.
            }
        }

        return HomePageList(request.name, items)
    }

    // Loads detailed information for a specific movie or TV series.
    override suspend fun load(url: String): LoadResponse? {
        // Fetch and parse the HTML document for the item's detail page.
        val document = get(url).parsed<String>().let { Jsoup.parse(it) }

        // Extract the main title of the anime/movie.
        // --- ADJUST CSS SELECTOR HERE ---
        val title = document.selectFirst("h1.heading-name")?.text()?.trim() ?: return null

        // Extract the poster image URL.
        // --- ADJUST CSS SELECTOR HERE ---
        val posterUrl = document.selectFirst(".film-poster img")?.attr("data-src")
            ?: document.selectFirst(".film-poster img")?.attr("src")

        // Extract the plot or description.
        // --- ADJUST CSS SELECTOR HERE ---
        val plot = document.selectFirst(".description-text")?.text()?.trim()

        // Extract genres.
        // --- ADJUST CSS SELECTOR HERE ---
        val genres = document.select(".genres a").map { it.text() }

        // Extract release year.
        // --- ADJUST CSS SELECTOR HERE ---
        val year = document.selectFirst(".aniskip-info-item:contains(Released) .aniskip-info-value")?.text()?.toIntOrNull()

        // Extract rating.
        // --- ADJUST CSS SELECTOR HERE ---
        val rating = document.selectFirst(".rating-value")?.text()?.toFloatOrNull()

        // Determine if it's a TV series or a movie based on the presence of an episode list.
        // --- ADJUST CSS SELECTOR HERE (e.g., check for a specific tab or section ID) ---
        val tvType = if (document.selectFirst(".tab-content #episodes") != null) TvType.TvSeries else TvType.Movie

        // Extract recommendations (optional).
        // This selector assumes recommendations are listed similarly to main page items.
        // --- ADJUST CSS SELECTOR HERE ---
        val recommendations = document.select(".film_list-wrap .flw-item").mapNotNull { element ->
            val recTitle = element.selectFirst(".film-name a")?.text() ?: return@mapNotNull null
            val recHref = element.selectFirst(".film-name a")?.attr("href") ?: return@mapNotNull null
            val recPosterUrl = element.selectFirst(".film-poster img")?.attr("data-src")
                ?: element.selectFirst(".film-poster img")?.attr("src")

            newSearchResponse(recTitle, "$mainUrl$recHref", TvType.Movie) { // Assuming movie type for recommended items, adjust if needed
                this.posterUrl = recPosterUrl
            }
        }

        if (tvType == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            // Anikai often lists episodes directly in the HTML or loads them via a simple AJAX call.
            // This example assumes episodes are listed directly in a container, e.g., with id 'episodes-content'.
            // --- ADJUST CSS SELECTOR HERE ---
            val episodeList = document.select("#episodes-content li.episode-item")

            episodeList.forEach { epElement ->
                // Extract episode number from 'data-episode' attribute.
                // --- ADJUST ATTRIBUTE/CSS SELECTOR HERE ---
                val epNum = epElement.attr("data-episode").toIntOrNull()
                // Extract episode title from a span with class 'title'.
                // --- ADJUST CSS SELECTOR HERE ---
                val epTitle = epElement.selectFirst(".title")?.text()?.trim()
                // Extract episode URL from the anchor tag.
                // --- ADJUST CSS SELECTOR HERE ---
                val epHref = epElement.selectFirst("a")?.attr("href")

                if (epNum != null && epHref != null) {
                    episodes.add(newEpisode(epHref) {
                        this.season = 1 // Anikai typically doesn't specify seasons explicitly, assume season 1.
                        this.episode = epNum
                        this.name = epTitle
                        // Add episode thumbnail or plot if available and needed.
                    })
                }
            }
            // Often episodes are listed from newest to oldest, so reverse to get chronological order.
            return newTvSeriesLoadResponse(title, url, tvType, episodes.reversed()) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.genres = genres
                this.year = year
                this.rating = rating
                this.recommendations = recommendations
            }
        } else {
            // For movies, the detail page URL itself is often the 'dataUrl' used to find links.
            return newMovieLoadResponse(title, url, tvType, url) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.genres = genres
                this.year = year
                this.rating = rating
                this.recommendations = recommendations
            }
        }
    }

    // Loads video links for a given movie or episode.
    override suspend fun loadLinks(
        data: String,
        isDub: Boolean, // This parameter is usually passed to extractors if they need it.
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Fetch and parse the HTML document for the given data URL (movie page or episode page).
        val document = get(data).parsed<String>().let { Jsoup.parse(it) }

        // Most streaming sites embed a video player within an iframe or load it via JavaScript.
        // We will try to find common patterns for player URLs.

        // 1. Look for iframes that are likely video players.
        // --- ADJUST CSS SELECTORS HERE for the iframe containing the video player ---
        // Common selectors include iframes within player containers, or generic embed iframes.
        val playerUrl = document.selectFirst("#player-container iframe")?.attr("src")
            ?: document.selectFirst(".play-video iframe")?.attr("src")
            ?: document.selectFirst("iframe[src*=/embed/]")?.attr("src") // Generic embed iframe often has '/embed/'

        if (playerUrl != null) {
            // Ensure the URL is absolute. Some sites use relative paths or '//' for protocol-relative URLs.
            val absolutePlayerUrl = if (playerUrl.startsWith("//")) "https:$playerUrl" else playerUrl

            // CloudStream has a powerful Extractor API that can handle many common embed players
            // (e.g., StreamSB, DoodStream, etc.). We pass the absolute player URL to it.
            // If the URL is handled by a known internal extractor, this will find and call it.
            return ExtractorApi.callExtractor(absolutePlayerUrl, mainUrl, subtitleCallback, callback)
        }

        // 2. Fallback: Check for direct <video> tags with <source> children.
        // This is less common for full episodes/movies but sometimes present.
        // --- ADJUST CSS SELECTOR HERE if direct <video> sources are used ---
        val videoSources = document.select("video source")
        for (source in videoSources) {
            val src = source.attr("src")
            // Try to infer quality from the URL or attributes if possible.
            // For simplicity, we use Qualities.Unknown here.
            val quality = Qualities.Unknown.value

            if (src.isNotBlank()) {
                callback(
                    ExtractorLink(
                        "Anikai", // Source name
                        "Direct Video", // Link name shown to the user
                        src,
                        mainUrl, // Referer header (important for some sites)
                        quality,
                        false // Set to true if it's an HLS (m3u8) stream
                    )
                )
                return true // Found a direct video link.
            }
        }

        // 3. Advanced Scenario: Sometimes, the video URL is embedded within JavaScript.
        // This requires parsing script tags and their content. This is highly site-specific and complex.
        // Example (hypothetical, you'd need to inspect Anikai's actual JS):
        /*
        val script = document.selectFirst("script:contains(playerConfig)")?.html()
        if (script != null) {
            val videoJson = Regex("playerConfig = (\\{.*?\\});").find(script)?.groupValues?.get(1)
            if (videoJson != null) {
                // You would need to add 'app.cloudstream3.utils.AppUtils.parseJson' for this.
                val config = app.cloudstream3.utils.AppUtils.parseJson<Map<String, Any>>(videoJson)
                val videoUrl = config["videoUrl"] as? String
                if (videoUrl != null) {
                     callback(ExtractorLink("Anikai", "JS Extracted", videoUrl, mainUrl, Qualities.P1080.value, false))
                     return true
                }
            }
        }
        */

        return false // No links found or extracted.
    }

    // Handles search queries.
    override suspend fun search(query: String, list: List<SearchResponse>): List<SearchResponse> {
        // Construct the search URL. Anikai uses '/search?keyword='.
        val url = "$mainUrl/search?keyword=$query"
        val document = get(url).parsed<String>().let { Jsoup.parse(it) }

        // The search results typically have a similar structure to the main page items.
        // --- ADJUST CSS SELECTOR HERE ---
        return document.select("div.film_list-wrap div.flw-item").mapNotNull { element ->
            val title = element.selectFirst(".film-name a")?.text() ?: return@mapNotNull null
            val href = element.selectFirst(".film-name a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = element.selectFirst(".film-poster img")?.attr("data-src")
                ?: element.selectFirst(".film-poster img")?.attr("src")

            // Simple heuristic to determine type: if it has an 'episodes' indicator, it's a series.
            // --- ADJUST CSS SELECTOR HERE if a different indicator is used for series ---
            val type = if (element.selectFirst(".episodes") != null) TvType.TvSeries else TvType.Movie

            newSearchResponse(title, "$mainUrl$href", type) {
                this.posterUrl = posterUrl
            }
        }
    }
}
