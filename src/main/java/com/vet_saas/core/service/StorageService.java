package com.vet_saas.core.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.vet_saas.core.exceptions.types.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final Cloudinary cloudinary;

    public String uploadFile(MultipartFile file, String folderName) {
        if (file.isEmpty()) {
            throw new BusinessException("El archivo de imagen está vacío");
        }

        // Validación de tipo de archivo (MIME Type)
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") &&
                !contentType.equals("image/png") &&
                !contentType.equals("image/webp"))) {
            throw new BusinessException("Solo se permiten archivos de imagen (JPG, PNG, WEBP)");
        }

        try {
            String fileName = UUID.randomUUID().toString();

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "vet-saas/" + folderName,
                            "public_id", fileName,
                            "resource_type", "auto"));

            return uploadResult.get("secure_url").toString();

        } catch (IOException e) {
            throw new BusinessException("Error al subir la imagen a la nube: " + e.getMessage());
        }
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return;
        }

        try {
            int vetSaasIndex = fileUrl.indexOf("vet-saas/");
            if (vetSaasIndex != -1) {
                String publicIdWithExt = fileUrl.substring(vetSaasIndex);
                String publicId = publicIdWithExt.contains(".")
                        ? publicIdWithExt.substring(0, publicIdWithExt.lastIndexOf('.'))
                        : publicIdWithExt;

                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            }
        } catch (Exception e) {
            System.err.println("Warning: failed to delete image from Cloudinary on cleanup: " + e.getMessage());
        }
    }
}