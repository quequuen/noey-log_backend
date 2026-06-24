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
@CrossOrigin(origins = "http://localhost:5175")
public class PostController {

    // 프론트엔드의 posts.json 물리 경로
    private final String JSON_PATH = Paths.get("..", "noey-log_frontend", "src", "data", "posts.json")
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
            File frontendDir = new File(Paths.get("..", "noey-log_frontend").toAbsolutePath().toString());

            // 1. git add .
            Process addProcess = new ProcessBuilder("git", "add", ".").directory(frontendDir).start();
            addProcess.waitFor();

            // 2. git commit -m "..."
            String commitMessage = "feat: 새 블로그 글 자동 업로드 (" + LocalDate.now() + ")";
            Process commitProcess = new ProcessBuilder("git", "commit", "-m", commitMessage).directory(frontendDir).start();
            commitProcess.waitFor();

            // 3. git push origin main && 에러 로그를 낚아채는 로직 추가
            Process pushProcess = new ProcessBuilder("git", "push", "origin", "main").directory(frontendDir).start();

            // 깃허브가 뱉은 에러 메시지를 자바 콘솔에 강제로 출력하기
            java.io.BufferedReader errorReader = new java.io.BufferedReader(new java.io.InputStreamReader(pushProcess.getErrorStream()));
            String errorLine;
            System.out.println("Error:");
            while ((errorLine = errorReader.readLine()) != null) {
                System.out.println("    Git: " + errorLine);
            }

            int pushResult = pushProcess.waitFor();
            System.out.println("Push 최종 결과 코드 (0이면 성공): " + pushResult);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return response;

    }
}