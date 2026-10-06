using System.IO;
using System.Collections;
using UnityEngine;
using UnityEngine.UI;

public class AssetBundleViewer : MonoBehaviour
{
    // Tempat menempelkan objek yang berhasil dimuat di dalam Scene
    public Transform spawnPoint; 
    
    // Teks UI untuk memunculkan pesan status/error
    public Text statusText; 

    /// <summary>
    /// Fungsi utama untuk memuat file .unity3d dari penyimpanan HP
    /// </summary>
    /// <param name="filePath">Jalur lengkap file (contoh: /storage/emulated/0/Download/karakter.unity3d)</param>
    public void LoadUnity3dFile(string filePath)
    {
        if (!File.Exists(filePath))
        {
            statusText.text = "Error: File tidak ditemukan di jalur tersebut!";
            return;
        }

        StartCoroutine(LoadBundleCoroutine(filePath));
    }

    private IEnumerator LoadBundleCoroutine(string filePath)
    {
        statusText.text = "Sedang memuat berkas .unity3d...";

        // 1. Memuat AssetBundle dari penyimpanan lokal secara Asynchronous
        AssetBundleCreateRequest bundleRequest = AssetBundle.LoadFromFileAsync(filePath);
        yield return bundleRequest;

        AssetBundle myLoadedAssetBundle = bundleRequest.assetBundle;
        if (myLoadedAssetBundle == null)
        {
            statusText.text = "Gagal memuat AssetBundle. File mungkin rusak atau versi Unity tidak cocok.";
            yield break;
        }

        // 2. Mengambil semua nama aset di dalam bundle (biasanya kita cari GameObject/Prefab)
        string[] assetNames = myLoadedAssetBundle.GetAllAssetNames();
        if (assetNames.Length == 0)
        {
            statusText.text = "Bundle kosong atau tidak berisi visual/GameObject.";
            myLoadedAssetBundle.Unload(false);
            yield break;
        }

        // 3. Memuat aset pertama yang ditemukan (biasanya model 3D utama beserta animasinya)
        AssetBundleRequest assetRequest = myLoadedAssetBundle.LoadAssetAsync<GameObject>(assetNames[0]);
        yield return assetRequest;

        GameObject prefab = assetRequest.assetObject as GameObject;
        if (prefab != null)
        {
            // 4. Memunculkan objek ke dalam game world agar visual & animasinya langsung jalan
            GameObject spawnedObject = Instantiate(prefab, spawnPoint.position, spawnPoint.rotation);
            statusText.text = $"Berhasil menampilkan: {prefab.name}";
        }
        else
        {
            statusText.text = "Aset utama di dalam file bukan merupakan objek visual/Prefab.";
        }

        // 5. Unload memory bundle, tetapi biarkan objek yang di-instantiate tetap hidup
        myLoadedAssetBundle.Unload(false);
    }
}
