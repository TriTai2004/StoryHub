package story.feature.user.controllers;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import story.common.dto.ApiResponse;
import story.common.dto.PageResponse;
import story.feature.user.dto.req.UpdateProfileRequest;
import story.feature.user.dto.res.*;
import story.feature.user.services.service.IUserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final IUserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<PublicProfileResponse>> getPublicProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getPublicProfile(userId)));
    }

    @GetMapping("/{userId}/posts")
    public ResponseEntity<ApiResponse<PageResponse<UserPostResponse>>> getPublicPosts(@PathVariable UUID userId,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userService.getPublicPosts(userId, pageable)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMe() {
        return ResponseEntity.ok(ApiResponse.success(userService.getMe()));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateMe(request)));
    }

    @GetMapping("/me/posts")
    public ResponseEntity<ApiResponse<PageResponse<UserPostResponse>>> getMyPosts(
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userService.getMyPosts(pageable)));
    }

    @GetMapping("/me/bookmarks")
    public ResponseEntity<ApiResponse<PageResponse<UserPostResponse>>> getMyBookmarks(
            @PageableDefault(size = 20, sort = {"createdAt", "post.id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userService.getMyBookmarks(pageable)));
    }
}
