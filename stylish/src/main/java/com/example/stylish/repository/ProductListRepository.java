package com.example.stylish.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.stylish.dto.ColorDto;
import com.example.stylish.dto.ProductDto;
import com.example.stylish.dto.VariantDto;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Repository
public class ProductListRepository {

    // Injecting the storage location from application properties
    @Value("${storage.location}")
    private String storageLocation;

    // Injecting the CDN URL from application properties
    @Value("${cdn.url}")
    private String cdnUrl;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int PAGE_SIZE = 6; // Page size for pagination

    /**
     * Retrieves a list of products based on the target, page, and mode.
     *
     * @param target The search target (category, title, or ID).
     * @param page The current page number.
     * @param mode The mode of search (1 for list, 2 for search by title, 3 for search by ID).
     * @return A list of ProductDto objects.
     */
    public List<ProductDto> getProductList(String target, int page, int mode) {

        int offset = page * PAGE_SIZE;
        String baseSql = "SELECT p.product_id, p.category, p.title, p.description, p.price, p.texture, p.wash, p.place, p.note, p.story, p.main_image " +
                         "FROM product p ";

        String sql;
        Object[] params;

        if (mode == 1) {
            String category = target;

            if ("all".equalsIgnoreCase(category)) {
                // Fetch all products with pagination
                sql = baseSql + "LIMIT ? OFFSET ?";
                params = new Object[]{PAGE_SIZE + 1, offset}; // additional one page is for checking if any next page exists
            } else {
                // Fetch products by category with pagination
                sql = baseSql + "WHERE p.category = ? LIMIT ? OFFSET ?";
                params = new Object[]{category, PAGE_SIZE + 1, offset}; // additional one page is for checking if any next page exists
            }

        } else if (mode == 2) {
            String keyword = "%" + target + "%";

            // Fetch products by title with pagination
            sql = baseSql + "WHERE p.title LIKE ? LIMIT ? OFFSET ?";
            params = new Object[]{keyword, PAGE_SIZE + 1, offset}; // additional one page is for checking if any next page exists

        } else {
            String id = target;

            // Fetch product by ID
            sql = baseSql + "WHERE p.product_id = ?";
            params = new Object[]{id};
        }

        // Query the database and map the results to ProductDto objects using ProductRowMapper
        List<ProductDto> products = jdbcTemplate.query(sql, params, new ProductRowMapper(cdnUrl, storageLocation));

        // Fetch additional details (colors, sizes, variants, images) for each product
        for (ProductDto product : products) {
            fetchProductDetails(product);
        }

        return products;
    }

    /**
     * Fetches additional details such as colors, sizes, variants, and images for a given product.
     *
     * @param product The product for which to fetch details.
     */
    private void fetchProductDetails(ProductDto product) {
        // Fetch available colors for the product
        String colorSql = "SELECT DISTINCT c.code, c.name FROM color c " +
                          "JOIN product_variant pv ON pv.color_code = c.code " +
                          "WHERE pv.product_id = ?";
        List<ColorDto> colors = jdbcTemplate.query(colorSql, new Object[]{product.getId()}, (ResultSet rs, int rowNum) -> {
            ColorDto color = new ColorDto();
            color.setCode(rs.getString("code"));
            color.setName(rs.getString("name"));
            return color;
        });
        product.setColors(colors);

        // Fetch available sizes for the product
        String sizeSql = "SELECT DISTINCT pv.size FROM product_variant pv WHERE pv.product_id = ?";
        List<String> sizes = jdbcTemplate.query(sizeSql, new Object[]{product.getId()}, (ResultSet rs, int rowNum) -> rs.getString("size"));

        // Sort sizes in order: S, M, L, XL, XXL
        List<String> sizeOrder = Arrays.asList("S", "M", "L", "XL", "XXL");

        sizes.sort((s1, s2) -> {
            int index1 = sizeOrder.indexOf(s1);
            int index2 = sizeOrder.indexOf(s2);
            return Integer.compare(index1, index2);
        });

        product.setSizes(sizes);

        // Fetch available variants (size, color, stock) for the product
        String variantSql = "SELECT pv.size, pv.color_code, pv.stock FROM product_variant pv WHERE pv.product_id = ?";
        List<VariantDto> variants = jdbcTemplate.query(variantSql, new Object[]{product.getId()}, (ResultSet rs, int rowNum) -> {
            VariantDto variant = new VariantDto();
            variant.setSize(rs.getString("size"));
            variant.setColorCode(rs.getString("color_code"));
            variant.setStock(rs.getInt("stock"));
            return variant;
        });
        product.setVariants(variants);

        // Fetch image paths for the product and prepend the CDN URL and storage location
        String imageSql = "SELECT pi.image_path FROM product_image pi WHERE pi.product_id = ?";
        List<String> images = jdbcTemplate.query(imageSql, new Object[]{product.getId()}, (ResultSet rs, int rowNum) -> cdnUrl + "/" + storageLocation + "/" + rs.getString("image_path"));
        product.setImages(images);
    }

    /**
     * Maps the ResultSet rows to ProductDto objects.
     * This is a static inner class that receives cdnUrl and storageLocation via constructor.
     */
    private static class ProductRowMapper implements org.springframework.jdbc.core.RowMapper<ProductDto> {

        private final String cdnUrl;
        private final String storageLocation;

        // Constructor to inject cdnUrl and storageLocation
        public ProductRowMapper(String cdnUrl, String storageLocation) {
            this.cdnUrl = cdnUrl;
            this.storageLocation = storageLocation;
        }

        @Override
        public ProductDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            ProductDto product = new ProductDto();
            product.setId(rs.getInt("product_id"));
            product.setCategory(rs.getString("category"));
            product.setTitle(rs.getString("title"));
            product.setDescription(rs.getString("description"));
            product.setPrice(rs.getDouble("price"));
            product.setTexture(rs.getString("texture"));
            product.setWash(rs.getString("wash"));
            product.setPlace(rs.getString("place"));
            product.setNote(rs.getString("note"));
            product.setStory(rs.getString("story"));
            // Prepend CDN URL and storage location to the main image path
            product.setMainImage(cdnUrl + "/" + storageLocation + "/" + rs.getString("main_image"));
            return product;
        }
    }
}
