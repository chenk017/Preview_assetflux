using System.IO;
using System.Collections;
using UnityEngine;
using UnityEngine.UI;

public class AssetBundleViewer : MonoBehaviour
{
    [Header("Pengaturan Spawn & Visual")]
    public Transform spawnPoint; 

    [Header("Komponen Antarmuka (UI)")]
    public Text statusText; 

    private GameObject currentSpawnedObject;

    public void OpenFilePicker()
    {
        statusText.text = "Membuka penyimpanan perangkat...";

        NativeFilePicker.Permission permission = NativeFilePicker.PickFile((path) =>
        {
            if (string.IsNullOrEmpty(path))
            {
                statusText.text = "Pemilihan berkas dibatalkan.";
                return;
            }
            LoadUnity3dFile(path);
        }, new string[] { "application/octet-stream" }); 

        if (permission == NativeFilePicker.Permission.Denied)
        {
            statusText.text = "Akses penyimpanan ditolak! Aktifkan izin di pengaturan HP.";
        }
    }

    public void LoadUnity3dFile(string filePath)
    {
        if (!File.Exists(filePath))
        {
            statusText.text = "Error: Berkas tidak ditemukan!";
            return;
        }

        if (currentSpawnedObject != null)
        {
            Destroy(currentSpawnedObject);
        }

        StartCoroutine(LoadBundleCoroutine(filePath));
    }

    private IEnumerator LoadBundleCoroutine(string filePath)
    {
        statusText.text = "Sedang mengekstrak berkas .unity3d...";

        AssetBundleCreateRequest bundleRequest = AssetBundle.LoadFromFileAsync(filePath);
        yield return bundleRequest;

        AssetBundle myLoadedAssetBundle = bundleRequest.assetBundle;
        if (myLoadedAssetBundle == null)
        {
            statusText.text = "Gagal memuat! Versi engine Unity tidak cocok.";
            yield break;
        }

        string[] assetNames = myLoadedAssetBundle.GetAllAssetNames();
        if (assetNames.Length == 0)
        {
            statusText.text = "Berkas kosong.";
            myLoadedAssetBundle.Unload(false);
            yield break;
        }

        statusText.text = "Merender visual dan animasi...";
        AssetBundleRequest assetRequest = myLoadedAssetBundle.LoadAssetAsync<GameObject>(assetNames);
        yield return assetRequest;

        GameObject prefab = assetRequest.assetObject as GameObject;
        if (prefab != null)
        {
            currentSpawnedObject = Instantiate(prefab, spawnPoint.position, spawnPoint.rotation);
            statusText.text = $"Sukses menampilkan: {prefab.name}";
        }
        else
        {
            statusText.text = "Gagal: Aset utama bukan objek visual (Prefab).";
        }

        myLoadedAssetBundle.Unload(false);
    }
}
