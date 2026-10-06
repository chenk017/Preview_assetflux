using System.IO;
using System.Collections;
using UnityEngine;
using UnityEngine.UI;

public class AssetBundleViewer : MonoBehaviour
{
    [Header("Pengaturan Spawn & Visual")]
    // Tempat menempelkan objek/model yang berhasil dimuat di dalam Scene
    public Transform spawnPoint; 
    
    [Header("Komponen Antarmuka (UI)")]
    // Teks UI untuk memunculkan pesan status/error ke pengguna
    public Text statusText; 

    // Menyimpan referensi objek yang sedang aktif di layar agar bisa dihapus saat memuat file baru
    private GameObject currentSpawnedObject;

    /// <summary>
    /// Fungsi utama yang disambungkan ke Tombol UI (On Click).
    /// Berfungsi membuka File Manager Android untuk memilih berkas .unity3d
    /// </summary>
    public void OpenFilePicker()
    {
        statusText.text = "Membuka penyimpanan perangkat...";

        // Membuka file manager Android dan memfilter tipe file
        NativeFilePicker.Permission permission = NativeFilePicker.PickFile((path) =>
        {
            if (string.IsNullOrEmpty(path))
            {
                statusText.text = "Pemilihan berkas dibatalkan.";
                return;
            }

            // Jika file berhasil dipilih, langsung jalankan proses pembacaan file
            LoadUnity3dFile(path);
        }, new string[] { "application/octet-stream" }); 

        // Mengecek apakah aplikasi diizinkan mengakses penyimpanan HP
        if (permission == NativeFilePicker.Permission.Denied)
        {
            statusText.text = "Akses penyimpanan ditolak! Berikan izin di pengaturan HP.";
        }
    }

    /// <summary>
    /// Memvalidasi apakah file yang dipilih benar-benar ada di penyimpanan HP
    /// </summary>
    public void LoadUnity3dFile(string filePath)
    {
        if (!File.Exists(filePath))
        {
            statusText.text = "Error: Berkas tidak ditemukan di jalur tersebut!";
            return;
        }

        // Hapus model lama terlebih dahulu sebelum memuat yang baru agar memori HP lega
        if (currentSpawnedObject != null)
        {
            Destroy(currentSpawnedObject);
        }

        // Jalankan Coroutine untuk memproses AssetBundle secara Asynchronous (tidak membuat HP lag)
        StartCoroutine(LoadBundleCoroutine(filePath));
    }

    /// <summary>
    /// Proses pembongkaran file .unity3d dan menampilkan visual beserta animasinya
    /// </summary>
    private IEnumerator LoadBundleCoroutine(string filePath)
    {
        statusText.text = "Sedang membaca dan mengekstrak berkas .unity3d...";

        // 1. Memuat AssetBundle dari penyimpanan lokal secara bertahap (Async)
        AssetBundleCreateRequest bundleRequest = AssetBundle.LoadFromFileAsync(filePath);
        yield return bundleRequest;

        AssetBundle myLoadedAssetBundle = bundleRequest.assetBundle;
        if (myLoadedAssetBundle == null)
        {
            statusText.text = "Gagal memuat! Berkas mungkin rusak atau versi engine Unity tidak cocok.";
            yield break;
        }

        // 2. Mengambil daftar semua aset yang ada di dalam berkas .unity3d tersebut
        string[] assetNames = myLoadedAssetBundle.GetAllAssetNames();
        if (assetNames.Length == 0)
        {
            statusText.text = "Berkas kosong atau tidak mengandung visual/GameObject.";
            myLoadedAssetBundle.Unload(false);
            yield break;
        }

        // 3. Memuat aset pertama yang ditemukan (biasanya berupa model 3D/Prefab utama)
        statusText.text = "Merender visual dan animasi...";
        AssetBundleRequest assetRequest = myLoadedAssetBundle.LoadAssetAsync<GameObject>(assetNames[0]);
        yield return assetRequest;

        GameObject prefab = assetRequest.assetObject as GameObject;
        if (prefab != null)
        {
            // 4. Memunculkan model 3D ke titik Spawn. Semua komponen Animasi & Visual bawaan game akan otomatis ikut berjalan
            currentSpawnedObject = Instantiate(prefab, spawnPoint.position, spawnPoint.rotation);
            statusText.text = $"Berhasil menampilkan visual: {prefab.name}";
        }
        else
        {
            statusText.text = "Gagal: Aset utama di dalam berkas bukan merupakan objek visual (Prefab).";
        }

        // 5. Bersihkan memori AssetBundle mentah, namun biarkan model yang sudah tampil tetap hidup
        myLoadedAssetBundle.Unload(false);
    }
}
