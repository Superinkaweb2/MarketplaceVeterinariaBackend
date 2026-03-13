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
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo de imagen está vacío o es nulo");
        }

        // Validación de tipo de archivo (MIME Type) - Más permisivo con el prefijo image/
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("El archivo debe ser una imagen. Tipo detectado: " + contentType);
        }

        try {
            String fileName = UUID.randomUUID().toString();
            String finalFolder = "vet-saas/" + (folderName.startsWith("/") ? folderName.substring(1) : folderName);

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", finalFolder,
                            "public_id", fileName,
                            "resource_type", "auto"));

            if (uploadResult == null || !uploadResult.containsKey("secure_url")) {
                throw new BusinessException("Cloudinary no devolvió una URL válida");
            }

            return uploadResult.get("secure_url").toString();

        } catch (Exception e) {
            System.err.println("Error fatal en Cloudinary: " + e.getMessage());
            e.printStackTrace();
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