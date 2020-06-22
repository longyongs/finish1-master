package com.teamadhoc.wifigroupstream.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.teamadhoc.wifigroupstream.R;
import com.teamadhoc.wifigroupstream.SongsManager;
import com.teamadhoc.wifigroupstream.Timer;
import com.teamadhoc.wifigroupstream.Utilities;

public class DJMusicFragment extends Fragment implements OnCompletionListener,  //공개 클래스 DJMusicFragment 확장 Fragment 는 OnCompletionListener를 구현합니다 .
        SeekBar.OnSeekBarChangeListener {
    private final static String TAG = "DJMusicFragment";
    private ImageButton btnPlay;
    private ImageButton btnNext;
    private ImageButton btnPrevious;
    private ImageButton btnPlaylist;
    private ImageButton btnRepeat;
    private ImageButton btnShuffle;
    private SeekBar songProgressBar;
    private TextView songTitleLabel;
    private TextView songCurrentDurationLabel;
    private TextView songTotalDurationLabel;
    private ProgressDialog syncProgress;

    // 미디어 플레이어
    private MediaPlayer mp;
    // UI 타이머, 진행률 표시 줄 등을 업데이트하는 핸들러입니다.
    private Handler handler = new Handler();
    private SongsManager songManager;
    private Utilities utils;
    private int currentSongIndex = 0;
    // 음악 재개
    private int currentPlayPosition = PLAY_FROM_BEGINNING;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    private ArrayList<HashMap<String, String>> songsList = new ArrayList<HashMap<String, String>>(); //새 아티스트

    private DJActivity activity = null;
    private View contentView = null;
    private Timer musicTimer = null;
    private final static long DELAY = 4500; //지연 딜레이?
    private final static int PLAY_FROM_BEGINNING = 0;

    private String[] musicList;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (DJActivity) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        contentView = inflater.inflate(R.layout.fragment_dj_music, null);

        // 모든 플레이어 버튼
        btnPlay = (ImageButton) contentView.findViewById(R.id.btnPlay);
        btnNext = (ImageButton) contentView.findViewById(R.id.btnNext);
        btnPrevious = (ImageButton) contentView.findViewById(R.id.btnPrevious);
        btnPlaylist = (ImageButton) contentView.findViewById(R.id.btnPlaylist);
        btnRepeat = (ImageButton) contentView.findViewById(R.id.btnRepeat);
        btnShuffle = (ImageButton) contentView.findViewById(R.id.btnShuffle);
        songProgressBar = (SeekBar) contentView.findViewById(R.id.songProgressBar);
        songTitleLabel = (TextView) contentView.findViewById(R.id.songTitle);
        songCurrentDurationLabel = (TextView) contentView.findViewById(R.id.songCurrentDurationLabel);
        songTotalDurationLabel = (TextView) contentView.findViewById(R.id.songTotalDurationLabel);

        // 진행률 표시 줄 대화 상자 준비
        syncProgress = new ProgressDialog(activity, AlertDialog.THEME_HOLO_DARK);
        syncProgress.setCancelable(false);
        syncProgress.setInverseBackgroundForced(true);
        syncProgress.setMessage("Get Ready to Enjoy Your Music!");
        syncProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);

        mp = new MediaPlayer();
        songManager = new SongsManager(getActivity());
        utils = new Utilities();

        // Listeners
        songProgressBar.setOnSeekBarChangeListener(this);
        mp.setOnCompletionListener(this);

        // Getting all songs list
        songsList = songManager.getPlayList();

        /**
         * 재생 버튼 클릭 이벤트는 노래를 재생하고 버튼을 일시 중지하도록 변경합니다.
         * 이미지 일
         *
         *
         *
         * 4
         * 시정지를 클릭하면 노래가 일시 정지되고 버튼이 변경되어 이미지가 재생됩니다.
         */
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // 이미 재생 중인지 확인합니다. 일시 중지 버튼 역할을합니다.
                if (mp != null && mp.isPlaying()) {
                    // 음악 재생을 일시 정지하고 현재 재생 위치를 저장합니다
                    mp.pause();
                    currentPlayPosition = mp.getCurrentPosition();
                    activity.stopRemoteMusic();
                    // 버튼 이미지를 재생 버튼으로 변경
                    btnPlay.setImageResource(R.drawable.btn_play);
                } else {
                    // 노래 재개
                    if (mp != null) {
                        // 음악 재생을 재개
                        playSong(currentSongIndex, currentPlayPosition);
                        // 버튼 이미지를 일시 정지 버튼으로 변경
                        btnPlay.setImageResource(R.drawable.btn_pause);
                    }
                }
            }
        });

        /**
         * 다음 버튼 클릭 이벤트는 currentSongIndex + 1을 사용하여 다음 노래를 재생합니다.
         */
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (isShuffle) {
                    // 셔플이 켜져 있습니다-임의의 노래를 재생하십시오
                    Random rand = new Random();
                    currentSongIndex = rand.nextInt(songsList.size());
                    playSong(currentSongIndex, PLAY_FROM_BEGINNING);
                } else {
                    // 다음 노래가 있는지 확인
                    if (currentSongIndex < (songsList.size() - 1)) {
                        playSong(++currentSongIndex, PLAY_FROM_BEGINNING);
                    } else {
                        //첫 노래를 재생
                        playSong(0, PLAY_FROM_BEGINNING);
                        currentSongIndex = 0;
                    }
                }
            }
        });

        /**
         * 뒤로 버튼 클릭 이벤트는 currentSongIndex에 의해 이전 노래를 재생합니다-1
         */
        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (isShuffle) {
                    // 셔플이 켜져 있습니다-임의의 노래를 재생하십시오
                    Random rand = new Random();
                    currentSongIndex = rand.nextInt(songsList.size());
                    playSong(currentSongIndex, PLAY_FROM_BEGINNING);
                } else {
                    if (currentSongIndex > 0) {
                        playSong(--currentSongIndex, PLAY_FROM_BEGINNING);;
                    } else {
                        // 마지막 노래 재생
                        playSong(songsList.size() - 1, PLAY_FROM_BEGINNING);
                        currentSongIndex = songsList.size() - 1;
                    }
                }
            }
        });

        /**
         * 반복 버튼에 대한 버튼 클릭 이벤트는 반복 플래그를 토글합니다.
         */
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (isRepeat) {
                    isRepeat = false;
                    Toast.makeText(contentView.getContext(), "Repeat is OFF", Toast.LENGTH_SHORT).show();
                    btnRepeat.setImageResource(R.drawable.btn_repeat);
                } else {
                    // 반복을 true로 변경
                    isRepeat = true;
                    Toast.makeText(contentView.getContext(), "Repeat is ON", Toast.LENGTH_SHORT).show();
                    // 셔플을 false로 변경
                    isShuffle = false;
                    btnRepeat.setImageResource(R.drawable.btn_repeat_focused);
                    btnShuffle.setImageResource(R.drawable.btn_shuffle);
                }
            }
        });

        /**
         * 셔플 버튼의 버튼 클릭 이벤트는 셔플 플래그를 토글합니다.
         */
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (isShuffle) {
                    isShuffle = false;
                    Toast.makeText(contentView.getContext(), "Shuffle is OFF", Toast.LENGTH_SHORT).show();
                    btnShuffle.setImageResource(R.drawable.btn_shuffle);
                } else {
                    // 셔플을 true로 변경
                    isShuffle = true;
                    Toast.makeText(contentView.getContext(), "Shuffle is ON", Toast.LENGTH_SHORT).show();
                    // 반복을 false로 변경
                    isRepeat = false;
                    btnShuffle.setImageResource(R.drawable.btn_shuffle_focused);

                    btnRepeat.setImageResource(R.drawable.btn_repeat);
                }
            }
        });

        /**
         * 재생 목록 버튼에 대한 버튼 클릭 이벤트는 노래 목록을 표시하는 목록 활동을 시작합니다
         */
        btnPlaylist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        activity, AlertDialog.THEME_HOLO_DARK);
                musicList = getMusicList().toArray(new String[getMusicList().size()]);
                builder.setTitle("Select Song");
                builder.setSingleChoiceItems(musicList, -1, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item){
                        currentSongIndex = item;
                        // 사용자가 선택한 음악을 재생합니다
                        playSong(currentSongIndex, PLAY_FROM_BEGINNING);
                        dialog.dismiss();
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        return contentView;
    }

    // SD 카드에서 배열 목록 가져 오기
    private ArrayList<String> getMusicList() {
        ArrayList<HashMap<String, String>> songsListData = new ArrayList<HashMap<String, String>>();
        ArrayList<String> musicList = new ArrayList<String>();
        SongsManager songsManager = new SongsManager(getActivity());
        // SD 카드에서 모든 노래 가져 오기
        this.songsList = songsManager.getPlayList();

        // 재생 목록을 반복
        for (int i = 0; i < songsList.size(); i++) {
            HashMap<String, String> song = songsList.get(i);
            songsListData.add(song);
        }

        for (int i = 0; i < songsListData.size(); i++) {
            musicList.add(songsListData.get(i).get("songTitle"));
        }

        return musicList;
    }

    public void startSyncDialog() {
        syncProgress.show();

        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // 행률 표시 줄 대화 상자를 닫습니다.
                syncProgress.dismiss();
            }
        }).start();
    }

    public void playSong(int songIndex, int playPosition) {
        try {
            // 스피너 표시 및 모든 사용자 작업 중지
            startSyncDialog();

            // 먼저 원격 음악을 중지
            activity.stopRemoteMusic();

            if (songsList.isEmpty()) {
                Toast.makeText(contentView.getContext(), "Empty Playlist", Toast.LENGTH_SHORT).show();
                return;
            } else if (songsList.get(songIndex) == null) {
                Toast.makeText(contentView.getContext(),
                        "Can't play this song", Toast.LENGTH_SHORT).show();
                return;
            }

            String musicFPath = songsList.get(songIndex).get("songPath");
            String songTitle = songsList.get(songIndex).get("songTitle");
            mp.reset();

            // Get the music timer
            musicTimer = activity.getTimer();

            mp.setDataSource(musicFPath);
            songTitleLabel.setText("Now Playing: " + songTitle);

            // 이미지를 일시 정지하도록 버튼 이미지 변경
            btnPlay.setImageResource(R.drawable.btn_pause);

            mp.prepare(); // 미디어 파일이 동 기적으로 재생되기를 원하기 때문에 PreparingAsync가 작동하지 않습니다.

            // TODO : 음악을 버퍼링했는지 확인하십시오. 우리는 정말로 여러 번의 시작, 일시 정지가 필요합니까?
            // 음악을 버퍼링합니다 (현재 이것은 큰 HACK이며 많은 시간이 걸립니다)
            mp.start();
            mp.pause();
            mp.start();
            mp.pause();
            mp.start();
            mp.pause();
            mp.start();
            mp.pause();
            mp.start();
            mp.pause();

            long futurePlayTime = musicTimer.getCurrTime() + DELAY;

            // playRemoteMusic, 시간 감지
            activity.playRemoteMusic(musicFPath, futurePlayTime, playPosition);

            // 음악 타이머가 향후 재생시기를 결정하도록합니다.
            musicTimer.playFutureMusic(mp, futurePlayTime, playPosition);

            // 노래 진행률 표시 줄 값 설정
            songProgressBar.setProgress(0);
            songProgressBar.setMax(100);

            // 노래 진행률 표시 줄 업데이트
            updateProgressBar();
        }
        catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException");
        }
        catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException");
        }
        catch (IOException e) {
            Log.e(TAG, "IOException");
        }
    }

    /**
     * 검색 바의 업데이트 타이머
     */
    public void updateProgressBar() {
        // 바를 초기화
        long totalDuration = mp.getDuration();

        // 총 지속 시간 표시
        songTotalDurationLabel.setText("" + utils.milliSecondsToTimer(totalDuration));
        // 재생 완료 시간 표시
        songCurrentDurationLabel.setText("" + utils.milliSecondsToTimer(currentPlayPosition));

        // 진행률 표시 줄 업데이트
        int progress = (int) (utils.getProgressPercentage(currentPlayPosition, totalDuration));
        songProgressBar.setProgress(progress);

        // Running updateTimeTask after 100 milliseconds
        handler.postDelayed(updateTimeTask, 100);
    }

    /**
     * 노래 진행을위한 백그라운드 실행 가능 스레드
     */
    private Runnable updateTimeTask = new Runnable() {
        public void run() {
            if (mp == null) {
                return;
            }

            // 음악이 재생중인 경우에만 진행률을 업데이트합니다
            if (mp.isPlaying()) {
                long totalDuration = mp.getDuration();
                currentPlayPosition = mp.getCurrentPosition();

                // 총 지속 시간 표시
                songTotalDurationLabel.setText("" + utils.milliSecondsToTimer(totalDuration));
                // 재생 완료 시간 표시
                songCurrentDurationLabel.setText("" + utils.milliSecondsToTimer(currentPlayPosition));

                // Updating progress bar
                int progress = (int) (utils.getProgressPercentage(currentPlayPosition, totalDuration));
                songProgressBar.setProgress(progress);
            }

            // Running this thread after 100 milliseconds
            handler.postDelayed(this, 100);
        }
    };

    /**
     *
     * */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {

    }

    /**
     * 사용자가 진행 처리기 이동을 중지 한 경우
     */
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Remove message Handler from updating progress bar
        handler.removeCallbacks(updateTimeTask);
    }

    /**
     * When user stops moving the progress handler
     */
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int totalDuration = mp.getDuration();
        // Get the new playing position
        currentPlayPosition = utils.progressToTimer(seekBar.getProgress(), totalDuration);
        playSong(currentSongIndex, currentPlayPosition);
    }

    /**
     *송 재생 완료 :
     *-반복이 ON 인 경우 : 동일한 노래를 다시 재생
     *-셔플이 켜져있는 경우 : 임의 노래 재생
     *-기타 : 다음 노래 재생
     */
    @Override
    public void onCompletion(MediaPlayer arg0) {
        if (isRepeat) {
            // Repeat is on - play same song again
            playSong(currentSongIndex, PLAY_FROM_BEGINNING);
        } else if (isShuffle) {
            // 플이 켜져 있습니다-임의의 노래를 재생하십시오
            Random rand = new Random();
            currentSongIndex = rand.nextInt(songsList.size());
            playSong(currentSongIndex, PLAY_FROM_BEGINNING);
        } else {
            // 반복 또는 셔플 없음-다음 노래 재생
            if (currentSongIndex < (songsList.size() - 1)) {
                playSong(++currentSongIndex, PLAY_FROM_BEGINNING);
            } else {
                // 첫 노래를 재생
                playSong(0, PLAY_FROM_BEGINNING);
                currentSongIndex = 0;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mp.release();
        mp = null;
    }
}