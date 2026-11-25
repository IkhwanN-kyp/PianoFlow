package id.arus.piano;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.*;

/** Offline, bilingual guide arranged by task rather than one long dialog. */
public final class HelpActivity extends Activity {
  private LinearLayout content, navigation;
  private ScrollView article;
  private final String[][] titles = {
    {"Mulai di sini", "Getting started"}, {"Bermain bebas & label", "Free play & labels"},
    {"Sambungan MIDI / USB", "MIDI / USB connection"}, {"Latihan lagu", "Song practice"},
    {"Suara & sustain", "Sound & sustain"}, {"Konversi audio", "Audio conversion"},
    {"Partitur & PDF", "Sheet music & PDF"},
    {"Pembaruan & pemecahan masalah", "Updates & troubleshooting"}
  };
  private final String[][][] sections = {
    {
      {
        "1. Mulai bermain",
        "1. Start playing",
        "Pilih Bermain bebas untuk langsung memainkan tuts layar atau piano MIDI. Tidak perlu"
            + " mengimpor lagu atau menekan Mulai.",
        "Choose Free play to use the screen keyboard or MIDI piano straight away. No song import or"
            + " Start button is needed."
      },
      {
        "2. Belajar dengan lagu",
        "2. Learn with a song",
        "Gunakan + Impor MIDI / XML untuk memilih .mid, .midi, atau MusicXML lokal. Pilih Latihan,"
            + " aktifkan Tunggu, lalu tekan Mulai. Berkas tersimpan di Perpustakaan tanpa batas"
            + " jumlah lagu.",
        "Use + Import MIDI / XML to select a local .mid, .midi or MusicXML file. Choose Practice,"
            + " enable Wait, then press Start. Songs are stored in Library with no song count"
            + " limit."
      },
      {
        "3. Sesuaikan tampilan",
        "3. Make it yours",
        "Label tuts tersedia di baris atas. Pengaturan menyediakan bahasa Indonesia/Inggris, tema"
            + " gelap/terang, suara, Bantuan, dan Tentang aplikasi.",
        "Key labels are available in the upper toolbar. Settings includes Indonesian/English,"
            + " dark/light theme, sound, Help and About."
      }
    },
    {
      {
        "Balok bergerak ke atas",
        "Rising note bars",
        "Tekan Bermain bebas pada baris atas. Nada muncul dari garis di atas keyboard, memanjang"
            + " selama tuts ditahan, lalu bergerak naik setelah dilepas. Nada rendah berwarna biru"
            + " dan nada mulai C4 berwarna hijau. Tidak ada skor, batas waktu, atau lagu"
            + " pengiring.",
        "Tap Free play in the upper toolbar. A note starts at the keyboard line, grows while you"
            + " hold the key and rises after release. Low notes are blue; notes from C4 upward are"
            + " mint. There is no score, time limit or song playback."
      },
      {
        "Huruf, angka, atau solmisasi",
        "Letters, numbers or solfege",
        "Pilih Label tuts: C D E F G A B; 1 2 3 4 5 6 7; Do Re Mi Fa Sol La Si; atau Tanpa label."
            + " Do dan angka 1 selalu berarti C. Tuts hitam memakai ♯, misalnya C♯ / 1♯ / Do♯. C4"
            + " menandai C tengah. Pilihan label tidak mengubah tinggi nada dan berlaku juga pada"
            + " latihan lagu.",
        "Choose Key labels: C D E F G A B; 1 2 3 4 5 6 7; Do Re Mi Fa Sol La Si; or No labels. Do"
            + " and number 1 always mean C. Black keys use ♯, for example C♯ / 1♯ / Do♯. C4 marks"
            + " middle C. Labels do not transpose pitches and also apply to song practice."
      },
      {
        "Berhenti atau kembali ke lagu",
        "Stop or return to your song",
        "Hentikan & bersihkan mematikan suara serta menghapus animasi. Kembali ke lagu"
            + " mengembalikan lagu dan posisi latihan dalam keadaan jeda. Pedal sustain"
            + " memperpanjang suara; panjang balok tetap menunjukkan lama tuts fisik ditekan.",
        "Stop & clear silences notes and clears the animation. Back to song returns to the same"
            + " song and practice position, paused. Sustain extends the sound; bar length follows"
            + " physical key presses."
      }
    },
    {
      {
        "Hubungkan Yamaha P-145",
        "Connect your Yamaha P-145",
        "Nyalakan piano dengan adaptor dayanya. Hubungkan USB TO HOST Type-B ke kabel USB 2.0 A–B"
            + " data (kurang dari 3 m), lalu ke adaptor USB-C OTG dan tablet. Buka MIDI / USB,"
            + " pilih Input · Android MIDI API, lalu port piano.",
        "Power the piano with its own adapter. Connect USB TO HOST Type-B using a USB 2.0 A–B data"
            + " cable (under 3 m), a USB-C OTG adapter and the tablet. Open MIDI / USB, choose"
            + " Input · Android MIDI API and select the piano port."
      },
      {
        "Jika port tidak muncul",
        "If no port appears",
        "Coba Cadangan · USB host MIDI 1 dan izinkan akses USB. Pilih salah satu jalur input, bukan"
            + " keduanya. Pastikan kabel mendukung data, OTG aktif, lalu cabut/pasang kembali."
            + " Tutup aplikasi lain yang sedang memakai piano.",
        "Try Fallback · USB host MIDI 1 and allow USB access. Select one input route, rather than"
            + " both. Check that the cable carries data and OTG is available, then reconnect. Close"
            + " other apps using the piano."
      },
      {
        "Output dan lampu",
        "Output and key lights",
        "Port output dipilih terpisah pada MIDI / USB. P-145 tidak memiliki lampu tuts. Panduan OUT"
            + " untuk perangkat lain hanya mengirim Note On/Off generik, bukan protokol lampu"
            + " khusus vendor. Pengujian fisik TGR-W09/P-145 serta pengukuran latensi belum"
            + " dilakukan.",
        "Select the output port separately in MIDI / USB. The P-145 has no illuminated keys. Guide"
            + " OUT only sends generic Note On/Off for other compatible devices, not"
            + " vendor-specific lighting commands. Physical TGR-W09/P-145 testing and latency"
            + " measurement are still outstanding."
      }
    },
    {
      {
        "Latihan atau Dengarkan",
        "Practice or Listen",
        "Latihan menilai nada yang Anda mainkan. Tombol Latihan / Dengarkan berganti antara"
            + " penilaian dan pemutaran MIDI. Tunggu ON menahan waktu hingga seluruh akor"
            + " dimainkan; OFF menilai awal nada dalam ±180 ms waktu lagu.",
        "Practice scores your input. The Practice / Listen button switches between scoring and MIDI"
            + " playback. Wait ON pauses time until the whole chord is played; OFF scores note"
            + " starts within ±180 ms of song time."
      },
      {
        "Tempo, tangan, dan loop",
        "Tempo, hands and loops",
        "Atur tempo 25–200%. Pilih Dua tangan / Kiri / Kanan atau tetapkan tangan per track. Untuk"
            + " mengulang bagian, geser ke awal lalu tekan Loop A; geser ke akhir dan tekan Loop B."
            + " Loop mati menonaktifkan pengulangan. Skor dihitung ulang setiap putaran.",
        "Set tempo from 25–200%. Select Both hands / Left / Right or assign roles per track. To"
            + " repeat a section, seek to its start and tap Loop A, seek to its end and tap Loop B."
            + " Loop off disables repeating. Scores reset on each pass."
      },
      {
        "Tuts layar dan keyboard komputer",
        "Screen keys and computer keyboard",
        "Keyboard layar mendukung beberapa sentuhan sekaligus. Keyboard komputer memakai A W S E D"
            + " F T G Y H U J K O L P ; mulai C4. Simpan dan pilih lagu melalui Perpustakaan;"
            + " perpindahan ke Bermain bebas tidak menghapus lagu.",
        "The screen keyboard supports multiple touches. Computer keys A W S E D F T G Y H U J K O L"
            + " P ; start at C4. Manage songs in Library; switching to Free play does not delete a"
            + " song."
      }
    },
    {
      {
        "Pilih sumber suara",
        "Choose sound output",
        "Instrumen menyediakan 128 program General MIDI, dengan Grand Piano 1 sebagai default."
            + " Audio: ON memakai tablet dan FluidSynth. Untuk tuts layar berbunyi di piano, pilih"
            + " port output MIDI lalu Audio > Yamaha P-145 / MIDI OUT. Saat memainkan piano fisik,"
            + " suara aslinya dapat berasal dari piano sendiri; input MIDI tidak dipantulkan"
            + " kembali ke output.",
        "Instrument offers 128 General MIDI programs with Grand Piano 1 as default. Audio: ON uses"
            + " tablet FluidSynth audio. To sound screen keys on your piano, select an output port,"
            + " then Audio > Yamaha P-145 / MIDI OUT. A physical piano may produce its own local"
            + " sound; MIDI input is not echoed to output."
      },
      {
        "Pedal, reverb, dan SoundFont",
        "Pedal, reverb and SoundFont",
        "Pedal MIDI CC64 dan tombol Sustain memperpanjang suara. Pengaturan > Suara mengatur reverb"
            + " serta polyphony. Impor SoundFont SF2 untuk bank lain; instrumen yang tersedia"
            + " mengikuti isi SF2. Gunakan hanya SoundFont yang diizinkan pemiliknya. Jika suara"
            + " terlalu berat, kurangi polyphony.",
        "MIDI CC64 pedals and the Sustain button extend sound. Settings > Sound configures reverb"
            + " and polyphony. Import a SoundFont SF2 for another bank; available instruments"
            + " depend on that file. Use SoundFonts you have permission to use. Lower polyphony if"
            + " playback struggles."
      }
    },
    {
      {
        "Dari audio ke MIDI",
        "From audio to MIDI",
        "Buka Konversi dan pilih MP3, WAV, M4A, atau FLAC. Pemrosesan berjalan offline. Basic Pitch"
            + " memakai model .tflite opsional dari rilis resmi; tanpa model tersedia deteksi"
            + " monofonik yang lebih sederhana. Tidak ada API berbayar.",
        "Open Convert and choose MP3, WAV, M4A or FLAC. Processing is offline. Basic Pitch uses an"
            + " optional .tflite model from official releases; without it a simpler monophonic"
            + " detector is available. There are no paid APIs."
      },
      {
        "Periksa hasil sebelum latihan",
        "Review before practicing",
        "Rekaman piano solo yang bersih memberi hasil lebih baik daripada lagu campuran. Dengarkan"
            + " preview, perbaiki nada/waktu/durasi, lalu ekspor MIDI atau pilih Latihan. Hasil"
            + " transkripsi dapat salah dan bukan partitur resmi rekaman.",
        "A clean solo piano recording works better than a mixed song. Preview it, correct"
            + " pitch/timing/duration, then export MIDI or choose Practice. Transcription can make"
            + " mistakes and is not an authoritative score of the recording."
      }
    },
    {
      {
        "Tiga tampilan belajar",
        "Three learning views",
        "Tampilan menawarkan Falling notes, Partitur, dan Hybrid. Partitur dan balok mengikuti"
            + " posisi lagu yang sama. Penyorotan menunjukkan nada latihan serta tuts yang sedang"
            + " ditekan. Gunakan Label tuts untuk bantuan membaca keyboard.",
        "View offers Falling notes, Sheet music and Hybrid. Score and falling notes follow the same"
            + " song position. Highlighting shows practice notes and pressed keys. Key labels help"
            + " you read the keyboard."
      },
      {
        "Impor dan ekspor",
        "Import and export",
        "Impor MusicXML mendukung score-partwise tanpa kompresi (.xml / .musicxml), bukan .mxl."
            + " Tombol PDF mengekspor partitur lagu yang dimuat. Notasi otomatis menggunakan"
            + " kuantisasi tampilan 1/16 dan belum mendukung engraving kompleks seperti semua tie,"
            + " repeat, atau tata letak penerbitan.",
        "MusicXML import supports uncompressed score-partwise (.xml / .musicxml), not .mxl. PDF"
            + " exports the loaded song’s score. Automatic notation uses display-only"
            + " sixteenth-note quantization and does not yet provide full ties, repeats or"
            + " publication layout."
      }
    },
    {
      {
        "Pembaruan resmi",
        "Official updates",
        "Buka Pengaturan > Software Update. Pilih kanal Beta untuk rilis uji atau Stable untuk"
            + " versi stabil yang sudah terbit. Periksa pembaruan secara manual, lalu Download &"
            + " Install Update. Aplikasi memverifikasi checksum, signature, package, dan kode versi"
            + " sebelum membuka installer sistem. Tidak perlu Google Play Services.",
        "Open Settings > Software Update. Choose Beta for preview releases or Stable for published"
            + " stable releases. Check manually, then Download & Install Update. The app verifies"
            + " checksum, signature, package and version code before opening the system installer."
            + " Google Play Services is not required."
      },
      {
        "Suara tersangkut atau tidak terdengar",
        "Stuck or missing sound",
        "Di Bermain bebas, tekan Hentikan & bersihkan. Periksa Audio, volume media tablet, port"
            + " MIDI, dan status koneksi. Jika terdengar dua suara saat memainkan P-145, gunakan"
            + " suara piano atau tablet sesuai kebutuhan. Keluar dari layar permainan akan"
            + " menghentikan nada aktif.",
        "In Free play, tap Stop & clear. Check Audio, tablet media volume, MIDI ports and"
            + " connection status. If playing the P-145 produces two sounds, use the piano or"
            + " tablet sound as appropriate. Leaving the playing screen stops active notes."
      },
      {
        "Data dan dukungan",
        "Data and support",
        "Pasang pembaruan menimpa versi lama; jangan uninstall agar data lokal tetap ada. Gunakan"
            + " MIDI, audio, dan SoundFont yang Anda miliki atau boleh gunakan. Informasi versi,"
            + " source code, lisensi, serta dukungan sukarela tersedia di Tentang aplikasi.",
        "Install updates over the old version; avoid uninstalling to keep local data. Use MIDI,"
            + " audio and SoundFonts you own or have permission to use. About includes version"
            + " information, source code, licenses and optional support."
      }
    }
  };

  @Override
  public void onCreate(Bundle state) {
    Ui.theme(this);
    super.onCreate(state);
    Ui.immersive(this);
    LinearLayout root = Ui.column(this), header = Ui.row(this);
    header.addView(Ui.button(this, "‹ Kembali", "‹ Back", this::finish));
    header.addView(Ui.text(this, tr("Bantuan & panduan", "Help & guide"), 23));
    root.addView(header);
    LinearLayout body = Ui.row(this);
    body.setGravity(android.view.Gravity.TOP);
    ScrollView menu = new ScrollView(this);
    navigation = Ui.column(this);
    for (int i = 0; i < titles.length; i++) {
      final int section = i;
      Button button = Ui.button(this, titles[i][0], titles[i][1], () -> show(section));
      button.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
      button.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));
      navigation.addView(button);
    }
    navigation.addView(
        Ui.button(
            this,
            "Tentang aplikasi",
            "About",
            () -> {
              startActivity(new Intent(this, AboutActivity.class));
              finish();
            }));
    menu.addView(navigation);
    body.addView(menu, new LinearLayout.LayoutParams(Ui.dp(this, 250), -1));
    article = new ScrollView(this);
    content = Ui.column(this);
    article.addView(content);
    body.addView(article, new LinearLayout.LayoutParams(0, -1, 1));
    root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(root);
    show(state == null ? 0 : state.getInt("section", 0));
  }

  private int selected;

  private String tr(String id, String en) {
    return Ui.tr(this, id, en);
  }

  private void show(int index) {
    selected = Math.max(0, Math.min(titles.length - 1, index));
    content.removeAllViews();
    TextView title = Ui.text(this, tr(titles[selected][0], titles[selected][1]), 26);
    title.setTextColor(Ui.accent(this));
    title.setTypeface(null, Typeface.BOLD);
    content.addView(title);
    for (String[] section : sections[selected]) {
      TextView heading = Ui.text(this, tr(section[0], section[1]), 19);
      heading.setTypeface(null, Typeface.BOLD);
      heading.setPadding(Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 8));
      content.addView(heading);
      TextView text = Ui.text(this, tr(section[2], section[3]), 16);
      text.setLineSpacing(Ui.dp(this, 5), 1);
      text.setTextIsSelectable(true);
      content.addView(text);
    }
    for (int i = 0; i < titles.length; i++) {
      Button button = (Button) navigation.getChildAt(i);
      button.setTextColor(i == selected ? Ui.accent(this) : Ui.fg(this));
      button.setTypeface(null, i == selected ? Typeface.BOLD : Typeface.NORMAL);
      button.setSelected(i == selected);
    }
    article.post(() -> article.scrollTo(0, 0));
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putInt("section", selected);
    super.onSaveInstanceState(state);
  }
}
