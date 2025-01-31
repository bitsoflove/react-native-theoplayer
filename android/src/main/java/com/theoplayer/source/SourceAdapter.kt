package com.theoplayer.source

import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.theoplayer.android.api.error.THEOplayerException
import com.facebook.react.bridge.ReadableMap
import com.theoplayer.android.api.source.SourceDescription
import com.theoplayer.android.api.source.TypedSource
import com.theoplayer.android.api.source.metadata.MetadataDescription
import com.theoplayer.android.api.source.addescription.AdDescription
import com.theoplayer.android.api.source.TextTrackDescription
import com.theoplayer.android.api.source.SourceType
import com.theoplayer.android.api.source.ssai.SsaiIntegration
import com.theoplayer.android.api.source.GoogleDaiTypedSource
import com.theoplayer.android.api.source.ssai.dai.GoogleDaiVodConfiguration
import com.theoplayer.android.api.source.ssai.dai.GoogleDaiLiveConfiguration
import com.theoplayer.android.api.source.ssai.YoSpaceDescription
import com.theoplayer.android.api.source.hls.HlsPlaybackConfiguration
import com.theoplayer.android.api.event.ads.AdIntegrationKind
import com.theoplayer.android.api.source.addescription.GoogleImaAdDescription
import com.theoplayer.android.api.player.track.texttrack.TextTrackKind
import com.theoplayer.android.api.source.metadata.ChromecastMetadataImage
import com.facebook.react.bridge.ReadableArray
import com.theoplayer.BuildConfig
import com.theoplayer.android.api.error.ErrorCode
import com.theoplayer.drm.ContentProtectionAdapter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList
import java.util.HashMap

private const val TAG = "SourceAdapter"
private const val PROP_CONTENT_PROTECTION = "contentProtection"
private const val PROP_LIVE_OFFSET = "liveOffset"
private const val PROP_HLS_DATERANGE = "hlsDateRange"
private const val PROP_HLS_PLAYBACK_CONFIG = "hls"
private const val PROP_TIME_SERVER = "timeServer"
private const val PROP_METADATA = "metadata"
private const val PROP_SSAI = "ssai"
private const val PROP_TYPE = "type"
private const val PROP_SRC = "src"
private const val PROP_SOURCES = "sources"
private const val PROP_DEFAULT = "default"
private const val PROP_LABEL = "label"
private const val PROP_KIND = "kind"
private const val PROP_TIME_OFFSET = "timeOffset"
private const val PROP_INTEGRATION = "integration"
private const val PROP_TEXT_TRACKS = "textTracks"
private const val PROP_POSTER = "poster"
private const val PROP_ADS = "ads"
private const val PROP_AVAILABILITY_TYPE = "availabilityType"
private const val ERROR_DAI_NOT_ENABLED = "Google DAI support not enabled."
private const val ERROR_UNSUPPORTED_SSAI_INTEGRATION = "Unsupported SSAI integration"
private const val ERROR_MISSING_SSAI_INTEGRATION = "Missing SSAI integration"
private const val ERROR_IMA_NOT_ENABLED = "Google IMA support not enabled."
private const val ERROR_UNSUPPORTED_CSAI_INTEGRATION = "Unsupported CSAI integration"
private const val ERROR_MISSING_CSAI_INTEGRATION = "Missing CSAI integration"

class SourceAdapter {
  private val gson = Gson()

  @Throws(THEOplayerException::class)
  fun parseSourceFromJS(source: ReadableMap): SourceDescription? {
    val hashmap = eliminateReadables(source)
    try {
      val json = gson.toJson(hashmap)
      val jsonSourceObject = JSONObject(json)

      // typed sources
      val typedSources = ArrayList<TypedSource>()

      // sources can be an array or single object
      val jsonSources = jsonSourceObject.optJSONArray(PROP_SOURCES)
      if (jsonSources != null) {
        for (i in 0 until jsonSources.length()) {
          val typedSource = parseTypedSource(jsonSources[i] as JSONObject)
          if (typedSource != null) {
            typedSources.add(typedSource)
          }
        }
      } else {
        val typedSource = parseTypedSource(jsonSourceObject.getJSONObject(PROP_SOURCES))
        if (typedSource != null) {
          typedSources.add(typedSource)
        }
      }

      // poster
      val poster = jsonSourceObject.optString(PROP_POSTER)

      // metadata
      var metadataDescription: MetadataDescription? = null
      val jsonMetadata = jsonSourceObject.optJSONObject(PROP_METADATA)
      if (jsonMetadata != null) {
        metadataDescription = parseMetadataDescription(jsonMetadata)
      }

      // ads
      val jsonAds = jsonSourceObject.optJSONArray(PROP_ADS)
      val ads = ArrayList<AdDescription>()
      if (jsonAds != null) {
        for (i in 0 until jsonAds.length()) {
          val jsonAdDescription = jsonAds[i] as JSONObject

          // Currently only ima-ads are supported.
          ads.add(parseAdFromJS(jsonAdDescription))
        }
      }

      // Side-loaded text tracks
      val textTracks = jsonSourceObject.optJSONArray(PROP_TEXT_TRACKS)
      val sideLoadedTextTracks = ArrayList<TextTrackDescription>()
      if (textTracks != null) {
        for (i in 0 until textTracks.length()) {
          val jsonTextTrack = textTracks[i] as JSONObject
          sideLoadedTextTracks.add(parseTextTrackFromJS(jsonTextTrack))
        }
      }
      val builder = SourceDescription.Builder(*typedSources.toTypedArray())
        .poster(poster)
        .ads(*ads.toTypedArray())
        .textTracks(*sideLoadedTextTracks.toTypedArray())
      if (metadataDescription != null) {
        builder.metadata(metadataDescription)
      }
      return builder.build()
    } catch (e: JSONException) {
      e.printStackTrace()
    }
    return null
  }

  @Throws(THEOplayerException::class)
  private fun parseTypedSource(jsonTypedSource: JSONObject): TypedSource? {
    try {
      var tsBuilder = TypedSource.Builder(jsonTypedSource.optString(PROP_SRC))
      val sourceType = parseSourceType(jsonTypedSource)
      if (jsonTypedSource.has(PROP_SSAI)) {
        val ssaiJson = jsonTypedSource.getJSONObject(PROP_SSAI)

        // Check for valid SsaiIntegration
        val ssaiIntegrationStr = ssaiJson.optString(PROP_INTEGRATION)
        if (!TextUtils.isEmpty(ssaiIntegrationStr)) {
          val ssaiIntegration = SsaiIntegration.from(ssaiIntegrationStr)
            ?: throw THEOplayerException(
              ErrorCode.AD_ERROR,
              "$ERROR_UNSUPPORTED_SSAI_INTEGRATION: $ssaiIntegrationStr"
            )
          when (ssaiIntegration) {
            SsaiIntegration.GOOGLE_DAI -> {
              if (!BuildConfig.EXTENSION_GOOGLE_DAI) {
                throw THEOplayerException(ErrorCode.AD_ERROR, ERROR_DAI_NOT_ENABLED)
              }
              tsBuilder = if (ssaiJson.optString(PROP_AVAILABILITY_TYPE) == "vod") {
                GoogleDaiTypedSource.Builder(
                  gson.fromJson(ssaiJson.toString(), GoogleDaiVodConfiguration::class.java)
                )
              } else {
                GoogleDaiTypedSource.Builder(
                  gson.fromJson(ssaiJson.toString(), GoogleDaiLiveConfiguration::class.java)
                )
              }
              // Prefer DASH if not SSAI type specified
              if (sourceType == null) {
                tsBuilder.type(SourceType.DASH)
              }
            }
            SsaiIntegration.YOSPACE -> tsBuilder.ssai(
              gson.fromJson(ssaiJson.toString(), YoSpaceDescription::class.java)
            )
            else -> throw THEOplayerException(
              ErrorCode.AD_ERROR,
              "$ERROR_UNSUPPORTED_SSAI_INTEGRATION: $ssaiIntegrationStr"
            )
          }
        } else {
          throw THEOplayerException(ErrorCode.AD_ERROR, ERROR_MISSING_SSAI_INTEGRATION)
        }
      }
      if (sourceType != null) {
        tsBuilder.type(sourceType)
      }
      if (jsonTypedSource.has(PROP_LIVE_OFFSET)) {
        tsBuilder.liveOffset(jsonTypedSource.getDouble(PROP_LIVE_OFFSET))
      }
      if (jsonTypedSource.has(PROP_HLS_DATERANGE)) {
        tsBuilder.hlsDateRange(jsonTypedSource.getBoolean(PROP_HLS_DATERANGE))
      }
      if (jsonTypedSource.has(PROP_HLS_PLAYBACK_CONFIG)) {
        val hlsConfig = gson.fromJson(
          jsonTypedSource[PROP_HLS_PLAYBACK_CONFIG].toString(),
          HlsPlaybackConfiguration::class.java
        )
        tsBuilder.hls(hlsConfig)
      }
      if (jsonTypedSource.has(PROP_TIME_SERVER)) {
        tsBuilder.timeServer(jsonTypedSource.getString(PROP_TIME_SERVER))
      }
      if (jsonTypedSource.has(PROP_CONTENT_PROTECTION)) {
        val drmConfig = ContentProtectionAdapter.drmConfigurationFromJson(
          jsonTypedSource.getJSONObject(PROP_CONTENT_PROTECTION)
        )
        if (drmConfig != null) {
          tsBuilder.drm(drmConfig)
        }
      }
      return tsBuilder.build()
    } catch (e: JSONException) {
      e.printStackTrace()
    }
    return null
  }

  @Throws(THEOplayerException::class)
  fun parseAdFromJS(map: ReadableMap): AdDescription? {
    val hashmap = eliminateReadables(map)
    return try {
      val jsonAdDescription = JSONObject(gson.toJson(hashmap))
      parseAdFromJS(jsonAdDescription)
    } catch (e: JSONException) {
      e.printStackTrace()
      null
    }
  }

  private fun parseSourceType(jsonTypedSource: JSONObject): SourceType? {
    val type = jsonTypedSource.optString(PROP_TYPE)
    if (type.isNotEmpty()) {
      if ("application/dash+xml" == type) {
        return SourceType.DASH
      }
      if ("application/x-mpegurl" == type) {
        return SourceType.HLSX
      }
      if ("application/vnd.theo.hesp+json" == type) {
        return SourceType.HESP
      }
      if ("application/vnd.apple.mpegurl" == type) {
        return SourceType.HLS
      }
      if ("video/mp4" == type) {
        return SourceType.MP4
      }
      if ("audio/mpeg" == type) {
        return SourceType.MP3
      }
    } else {
      // No type given, check for known extension.
      val src = jsonTypedSource.optString(PROP_SRC)
      if (src.endsWith(".mpd")) {
        return SourceType.DASH
      }
      if (src.endsWith(".m3u8")) {
        return SourceType.HLSX
      }
      if (src.endsWith(".mp4")) {
        return SourceType.MP4
      }
      if (src.endsWith(".mp3")) {
        return SourceType.MP3
      }
    }
    return null
  }

  @Throws(JSONException::class, THEOplayerException::class)
  fun parseAdFromJS(jsonAdDescription: JSONObject): AdDescription {
    val integrationKindStr = jsonAdDescription.optString(PROP_INTEGRATION)
    return if (!TextUtils.isEmpty(integrationKindStr)) {
      when (AdIntegrationKind.from(integrationKindStr)) {
        AdIntegrationKind.GOOGLE_IMA -> parseImaAdFromJS(
          jsonAdDescription
        )
        AdIntegrationKind.DEFAULT, AdIntegrationKind.THEO, AdIntegrationKind.FREEWHEEL, AdIntegrationKind.SPOTX -> {
          throw THEOplayerException(
            ErrorCode.AD_ERROR,
            "$ERROR_UNSUPPORTED_CSAI_INTEGRATION: $integrationKindStr"
          )
        }
        else -> {
          throw THEOplayerException(
            ErrorCode.AD_ERROR,
            "$ERROR_UNSUPPORTED_CSAI_INTEGRATION: $integrationKindStr"
          )
        }
      }
    } else {
      throw THEOplayerException(
        ErrorCode.AD_ERROR,
        "$ERROR_MISSING_CSAI_INTEGRATION: $integrationKindStr"
      )
    }
  }


  @Throws(THEOplayerException::class)
  private fun parseImaAdFromJS(jsonAdDescription: JSONObject): GoogleImaAdDescription {
    if (!BuildConfig.EXTENSION_GOOGLE_IMA) {
      throw THEOplayerException(ErrorCode.AD_ERROR, ERROR_IMA_NOT_ENABLED)
    }
    val source: String
    // Property `sources` is of type string | AdSource.
    val sourceObj = jsonAdDescription.optJSONObject(PROP_SOURCES)
    source = if (sourceObj != null) {
      sourceObj.optString(PROP_SRC)
    } else {
      jsonAdDescription.optString(PROP_SOURCES)
    }
    return GoogleImaAdDescription.Builder(source)
      .timeOffset(jsonAdDescription.optString(PROP_TIME_OFFSET))
      .build()
  }

  @Throws(JSONException::class)
  private fun parseTextTrackFromJS(jsonTextTrack: JSONObject): TextTrackDescription {
    val builder = TextTrackDescription.Builder(jsonTextTrack.optString(PROP_SRC))
      .isDefault(jsonTextTrack.optBoolean(PROP_DEFAULT))
      .label(jsonTextTrack.optString(PROP_LABEL))
      .kind(parseTextTrackKind(jsonTextTrack.optString(PROP_KIND))!!)
    return builder.build()
  }

  private fun parseTextTrackKind(kind: String?): TextTrackKind? {
    if (kind == null) {
      return null
    }
    when (kind) {
      "subtitles" -> return TextTrackKind.SUBTITLES
      "metadata" -> return TextTrackKind.METADATA
      "captions" -> return TextTrackKind.CAPTIONS
      "chapters" -> return TextTrackKind.CHAPTERS
      "descriptions" -> return TextTrackKind.DESCRIPTIONS
    }
    return null
  }

  private fun parseMetadataDescription(metadataDescription: JSONObject): MetadataDescription {
    val metadata = HashMap<String, Any>()
    val keys = metadataDescription.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      try {
        if (key == "images") {
          metadata[key] = parseMetadataImages(metadataDescription.getJSONArray(key))
        } else {
          metadata[key] = metadataDescription[key]
        }
      } catch (e: JSONException) {
        Log.w(TAG, "Failed to parse metadata key $key")
      }
    }
    return MetadataDescription(metadata)
  }

  @Throws(JSONException::class)
  private fun parseMetadataImages(metadataImages: JSONArray): List<ChromecastMetadataImage> {
    val imageList: MutableList<ChromecastMetadataImage> = ArrayList()
    for (i in 0 until metadataImages.length()) {
      imageList.add(parseMetadataImage(metadataImages.getJSONObject(i)))
    }
    return imageList
  }

  @Throws(JSONException::class)
  private fun parseMetadataImage(metadataImage: JSONObject): ChromecastMetadataImage {
    val width = if (metadataImage.has("width")) metadataImage.getInt("width") else null
    val height = if (metadataImage.has("height")) metadataImage.getInt("height") else null
    return ChromecastMetadataImage(metadataImage.optString("src"), width, height)
  }

  private fun eliminateReadables(readableMap: ReadableMap): HashMap<String, Any> {
    val hashMap = readableMap.toHashMap()
    val eliminatedHashMap = HashMap<String, Any>()
    for (entry in hashMap.entries) {
      var value = entry.value
      if (value is ReadableMap) {
        value = eliminateReadables(value)
      } else if (value is ReadableArray) {
        value = eliminateReadables(value)
      }
      eliminatedHashMap[entry.key] = value
    }
    return eliminatedHashMap
  }

  private fun eliminateReadables(readableArray: ReadableArray): ArrayList<Any> {
    val arrayList = readableArray.toArrayList()
    val eliminatedArrayList = ArrayList<Any>()
    for (o in arrayList) {
      var value = o
      if (value is ReadableMap) {
        value = eliminateReadables(value)
      } else if (value is ReadableArray) {
        value = eliminateReadables(value)
      }
      eliminatedArrayList.add(value)
    }
    return eliminatedArrayList
  }
}
