import type { Track } from './Track';
import type { TextTrackCue } from './TextTrackCue';

export type TextTrackType = 'srt' | 'ttml' | 'webvtt' | 'cea608' | '';

export type TextTrackKind = 'subtitles' | 'captions' | 'descriptions' | 'chapters' | 'metadata' | 'thumbnails';

export type TextTrackMode = 'disabled' | 'showing';

export interface TextTrack extends Track {
  /**
   * The kind of the text track, represented by a value from the following list:
   * <br/> - `'subtitles'`: The track contains subtitles.
   * <br/> - `'captions'`: The track contains closed captions, a translation of dialogue and sound effects.
   * <br/> - `'descriptions'`: The track contains descriptions, a textual description of the video.
   * <br/> - `'chapters'`: The track contains chapter titles.
   * <br/> - `'metadata'`: The track contains metadata. This track will not serve display purposes.
   */
  readonly kind: TextTrackKind;

  /**
   * The label of the text track.
   */
  label: string;

  /**
   * The language of the text track.
   */
  readonly language: string;

  /**
   * The identifier of the text track.
   *
   * @remarks
   * <br/> - This identifier can be used to distinguish between related tracks, e.g. tracks in the same list.
   */
  readonly id: string;

  /**
   * A unique identifier of the text track.
   *
   * @remarks
   * <br/> - This identifier is unique across tracks of a THEOplayer instance and can be used to distinguish between tracks.
   */
  readonly uid: number;

  /**
   * The mode of the text track, represented by a value from the following list:
   * <br/> - `'disabled'`: The track is disabled.
   * <br/> - `'showing'`: The track is showing.
   */
  mode: TextTrackMode;

  /**
   * The content type of the text track.
   */
  readonly type: TextTrackType;

  /**
   * The list of cues of the track.
   *
   * @remarks
   * <br/> - If the {@link TextTrack.mode} is `'disabled'`, this property is `null`.
   */
  cues: TextTrackCue[] | null;

  /**
   * The source of the text track.
   */
  readonly src: string;

  /**
   * Indicates whether the track contains Forced Narrative cues.
   * This may only be true for subtitle tracks where
   * <br/> - For DASH: the corresponding AdaptationSet contains a child Role with its value attribute equal to `'forced_subtitle'`
   * <br/> - For HLS: the corresponding #EXT-X-MEDIA tag contains the attributes TYPE=SUBTITLES and FORCED=YES (not supported yet)
   */
  readonly forced: boolean;
}

/**
 * Retain renderable tracks.
 * https://html.spec.whatwg.org/multipage/embedded-content.html#text-track-showing
 */
export function filterRenderableTracks(textTracks: TextTrack[] | undefined): TextTrack[] | undefined {
  return textTracks && textTracks.filter((textTrack) => textTrack.kind === 'subtitles' || textTrack.kind === 'captions');
}

/**
 * Retain first thumbnail track encountered in the textTracks list.
 */
export function filterThumbnailTracks(textTracks: TextTrack[] | undefined): TextTrack | undefined {
  return textTracks && textTracks.find(isThumbnailTrack);
}

/**
 * Query whether a track is a valid thumbnail track.
 */
export function isThumbnailTrack(textTrack: TextTrack | undefined): boolean {
  return !!textTrack && (textTrack.kind === 'thumbnails' || (textTrack.kind === 'metadata' && textTrack.label === 'thumbnails'));
}

export function hasTextTrack(textTrackList: TextTrack[], textTrack: TextTrack): boolean {
  return !!(textTrackList && textTrack && textTrackList.find((t) => t.uid === textTrack.uid));
}

export function removeTextTrack(textTrackList: TextTrack[], textTrack: TextTrack): TextTrack[] {
  return textTrackList && textTrack ? textTrackList.filter((t) => t.uid !== textTrack.uid) : textTrackList;
}

export function addTextTrack(textTrackList: TextTrack[], textTrack: TextTrack): TextTrack[] {
  return textTrackList && textTrack && !hasTextTrack(textTrackList, textTrack) ? [...textTrackList, textTrack] : textTrackList;
}

export function hasTextTrackCue(textTrack: TextTrack, cue: TextTrackCue): boolean {
  return !!(textTrack.cues && cue && textTrack.cues.find((c) => cue.uid === c.uid));
}

export function removeTextTrackCue(textTrack: TextTrack, cue: TextTrackCue) {
  if (textTrack && textTrack.cues && cue && !hasTextTrackCue(textTrack, cue)) {
    textTrack.cues = textTrack.cues.filter((c) => c.uid !== cue.uid);
  }
}

export function addTextTrackCue(textTrack: TextTrack, cue: TextTrackCue) {
  if (textTrack && textTrack.cues && cue && !hasTextTrackCue(textTrack, cue)) {
    textTrack.cues.push(cue);
  }
}

export function findTextTrackByUid(textTracks: TextTrack[], uid: number): TextTrack | undefined {
  return textTracks.find((t) => t.uid === uid);
}
