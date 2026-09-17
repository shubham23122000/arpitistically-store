package com.crochet.store.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.crochet.store.dto.ProductDto;
import com.crochet.store.entity.Product;
import com.crochet.store.repository.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final long MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;

    private final ProductRepository productRepository;
    private final Cloudinary cloudinary;

    public ProductController(
            ProductRepository productRepository,
            Cloudinary cloudinary) {
        this.productRepository = productRepository;
        this.cloudinary = cloudinary;
    }

    @GetMapping
    public List<ProductDto> listActiveProducts(
            @RequestParam(required = false) String category) {

        List<Product> products = (category == null || category.isBlank())
                ? productRepository.findByActiveTrue()
                : productRepository.findByCategoryAndActiveTrue(category);

        return products.stream().map(this::toDto).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ProductDto getProduct(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        return toDto(product);
    }

    @PostMapping("/admin")
    public ResponseEntity<ProductDto> createProduct(
            @Valid @RequestBody ProductDto dto) {

        Product product = new Product();
        applyDto(product, dto);

        Product saved = productRepository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    @PutMapping("/admin/{id}")
    public ProductDto updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductDto dto) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        applyDto(product, dto);
        return toDto(productRepository.save(product));
    }

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        product.setActive(false);
        productRepository.save(product);

        return ResponseEntity.noContent().build();
    }

    @PostMapping(
            value = "/admin/upload-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Map<String, String> uploadImage(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please choose an image to upload"
            );
        }

        if (file.getContentType() == null
                || !file.getContentType().startsWith("image/")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only image files are allowed"
            );
        }

        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image must be 10 MB or smaller"
            );
        }

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "crochet-store/products",
                            "resource_type", "image"
                    )
            );

            return Map.of("url", uploadResult.get("secure_url").toString());
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not upload image",
                    exception
            );
        }
    }

    private ProductDto toDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());
        dto.setCategory(product.getCategory());
        dto.setImageUrls(product.getImageUrls());
        return dto;
    }

    private void applyDto(Product product, ProductDto dto) {
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setCategory(dto.getCategory());

        if (dto.getImageUrls() != null) {
            product.setImageUrls(dto.getImageUrls());
        }
    }
}