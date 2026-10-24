import { useCallback, useEffect, useRef, useState } from 'react';

// 참고 곡으로 찾기 결과의 재생기 (NSU-111). 한 번에 한 곡만 재생합니다.
// - 참고 곡: 사용자가 고른 파일을 브라우저 안에서 바로 재생 (서버로 보내지 않음)
// - 추천 곡: 서버 연결 전이라 실제 음원 대신 그 곡의 BPM과 박자(16칸)를 짧은 소리로 들려줌
//   서버 연결 뒤에는 audio-url(Signed URL)을 <audio>에 넣는 방식으로 바꿉니다 (docs/api/02).
// track 모양: { key, duration(초), url? (참고 곡), bpm?, beats? (추천 곡) }

const LOOKAHEAD = 0.12; // 박자 소리를 미리 예약해 두는 시간(초)
const TICK_MS = 200; // 재생 위치를 화면에 반영하는 간격

// 짧은 박자 소리 하나 (accent: 마디의 첫 박 등 강한 박)
function click(ctx, time, accent) {
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.frequency.value = accent ? 880 : 620;
  gain.gain.setValueAtTime(0.0001, time);
  gain.gain.exponentialRampToValueAtTime(accent ? 0.35 : 0.2, time + 0.005);
  gain.gain.exponentialRampToValueAtTime(0.0001, time + 0.08);
  osc.connect(gain).connect(ctx.destination);
  osc.start(time);
  osc.stop(time + 0.1);
}

export default function useSongPlayer() {
  // key: 재생 중이거나 멈춘 곡, playing: 소리가 나는 중인지, position: 재생 위치(초)
  const [state, setState] = useState({ key: null, playing: false, position: 0, duration: 0 });
  const trackRef = useRef(null);
  const audioRef = useRef(null);
  const ctxRef = useRef(null);
  const beatRef = useRef(null); // { timer, startedAt, offset }
  const tickRef = useRef(null);

  // 지금 재생 위치(초)
  const currentPosition = useCallback(() => {
    const track = trackRef.current;
    if (!track) return 0;
    if (track.url) return audioRef.current?.currentTime || 0;
    const beat = beatRef.current;
    if (!beat) return state.position;
    return beat.offset + (ctxRef.current.currentTime - beat.startedAt);
  }, [state.position]);

  // 모든 소리를 멈춤 (위치는 호출한 쪽에서 정함)
  const silence = useCallback(() => {
    audioRef.current?.pause();
    if (beatRef.current) {
      clearInterval(beatRef.current.timer);
      beatRef.current = null;
    }
    clearInterval(tickRef.current);
  }, []);

  // 재생 위치를 주기적으로 화면에 반영하고, 끝까지 가면 멈춤
  const startTicking = useCallback(() => {
    clearInterval(tickRef.current);
    tickRef.current = setInterval(() => {
      const track = trackRef.current;
      if (!track) return;
      const position = Math.min(track.duration, currentPosition());
      if (position >= track.duration) {
        silence();
        setState((s) => ({ ...s, playing: false, position: 0 }));
        return;
      }
      setState((s) => ({ ...s, position }));
    }, TICK_MS);
  }, [currentPosition, silence]);

  // 박자 소리 재생 시작 (offset초 위치부터)
  const startBeats = useCallback((track, offset) => {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return false;
    if (!ctxRef.current) ctxRef.current = new AudioCtx();
    const ctx = ctxRef.current;
    ctx.resume?.();
    const step = 60 / track.bpm / 4; // 16칸 중 한 칸의 길이
    let index = Math.ceil(offset / step);
    let next = ctx.currentTime + (index * step - offset);
    const schedule = () => {
      while (next < ctx.currentTime + LOOKAHEAD) {
        const cell = index % track.beats.length;
        if (track.beats[cell]) click(ctx, next, cell % 4 === 0);
        next += step;
        index += 1;
      }
    };
    schedule();
    beatRef.current = { timer: setInterval(schedule, 25), startedAt: ctx.currentTime, offset };
    return true;
  }, []);

  // track을 at초 위치부터 재생
  const play = useCallback(
    (track, at = 0) => {
      silence();
      trackRef.current = track;
      const position = Math.min(Math.max(0, at), Math.max(0, track.duration - 0.5));
      setState({ key: track.key, playing: true, position, duration: track.duration });
      if (track.url) {
        if (!audioRef.current) audioRef.current = new Audio();
        const audio = audioRef.current;
        if (audio.src !== track.url) audio.src = track.url;
        audio.currentTime = position;
        // 재생이 거부되면 멈춤으로 표시. 다른 곡으로 바꾸면서 끊긴 경우(AbortError)나
        // 이미 다른 곡을 재생 중이면 지금 상태를 덮어쓰지 않음
        audio.play().catch((err) => {
          if (err?.name === 'AbortError' || trackRef.current !== track) return;
          silence();
          setState((s) => ({ ...s, playing: false }));
        });
      } else if (!startBeats(track, position)) {
        setState((s) => ({ ...s, playing: false }));
        return;
      }
      startTicking();
    },
    [silence, startBeats, startTicking],
  );

  // 멈춤 (위치는 그대로 기억)
  const pause = useCallback(() => {
    const position = currentPosition();
    silence();
    setState((s) => ({ ...s, playing: false, position }));
  }, [currentPosition, silence]);

  // 재생/멈춤 전환. 같은 곡이면 멈춘 자리부터, 다른 곡이면 처음부터
  const toggle = useCallback(
    (track) => {
      if (state.key === track.key && state.playing) pause();
      else play(track, state.key === track.key ? state.position : 0);
    },
    [pause, play, state.key, state.playing, state.position],
  );

  // 참고 곡 ↔ 이 곡 바꿔 듣기: 같은 재생 위치에서 다른 곡으로 이어서 재생
  const switchTo = useCallback(
    (track) => {
      const position = state.key ? currentPosition() : 0;
      if (state.playing) play(track, position);
      else {
        trackRef.current = track;
        setState({
          key: track.key,
          playing: false,
          position: Math.min(position, track.duration),
          duration: track.duration,
        });
      }
    },
    [currentPosition, play, state.key, state.playing],
  );

  // 재생 막대를 닫을 때: 멈추고 곡 선택도 지움
  const stop = useCallback(() => {
    silence();
    trackRef.current = null;
    setState({ key: null, playing: false, position: 0, duration: 0 });
  }, [silence]);

  // 화면을 떠나면 소리를 끔
  useEffect(
    () => () => {
      silence();
      ctxRef.current?.close?.();
    },
    [silence],
  );

  return { ...state, play, pause, toggle, switchTo, stop };
}
