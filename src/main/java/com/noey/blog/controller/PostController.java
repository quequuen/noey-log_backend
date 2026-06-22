package com.noey.blog.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin(origins = "http://localhost:5173")
public class PostController {

    // 프론트엔드의 posts.json 물리 경로
    private final String JSON_PATH = Paths.get("..", "frontend", "src", "data", "posts.json")
            .toAbsolutePath()
            .toString();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    public Map<String, Object> createPost(@RequestBody Map<String, Object> newPost) throws IOException {
        File jsonFile = new File(JSON_PATH);
        List<Map<String, Object>> posts = new ArrayList<>();

        // 기존 posts.json 파일이 있으면 읽어오기
        if (jsonFile.exists() && jsonFile.length() > 0) {
            posts = objectMapper.readValue(jsonFile, new TypeReference<List<Map<String, Object>>>() {});
        }

        // 새 글 데이터 가공 (ID 생성 및 날짜 추가)
        long nextId = posts.stream()
                .mapToLong(p -> Long.parseLong(p.get("id").toString()))
                .max()
                .orElse(0L) + 1;

        Map<String, Object> freshPost = new LinkedHashMap<>(); // 순서 보장을 위해 LinkedHashMap 사용
        freshPost.put("id", nextId);
        freshPost.put("type", newPost.get("type"));
        freshPost.put("title", newPost.get("title"));
        freshPost.put("content", newPost.get("content"));
        freshPost.put("date", LocalDate.now().toString()); // YYYY-MM-DD

        // 최신 글이 맨 위로 오도록 배열 가장 앞에 추가
        posts.add(0, freshPost);

        // posts.json 파일에 자동으로 덮어쓰기
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, posts);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "포스트가 성공적으로 파일에 저장되었습니다!");

        // 자동 깃허브 푸시 로직
        try {
            File rootDir = new File(Paths.get("..").toAbsolutePath().toString());

            new ProcessBuilder("git", "add", ".").directory(rootDir).start().waitFor();

            String commitMessage = "feat: 새 블로그 글 자동 업로드 (" + LocalDate.now() + ")";
            new ProcessBuilder("git", "commit", "-m", commitMessage).directory(rootDir).start().waitFor();

            new ProcessBuilder("git", "push", "origin", "main").directory(rootDir).start().waitFor();

            System.out.println("깃허브 자동 커밋 및 푸시 완료");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return response;

    }
}