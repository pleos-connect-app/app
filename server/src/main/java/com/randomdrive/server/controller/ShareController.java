package com.randomdrive.server.controller;

import com.randomdrive.server.service.TripService;
import com.randomdrive.server.service.TripService.MediaKind;
import com.randomdrive.server.service.TripService.MediaResult;
import com.randomdrive.server.service.TripService.SharePageResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;

/**
 * 공유 페이지 (F-08). 차량 화면의 QR을 휴대폰으로 찍으면 열리는 모바일 웹 페이지와, 거기 딸린 영상·썸네일 파일.
 * 페이지는 JSON API가 아니라 HTML을 돌려주므로 @RestController가 아니다.
 */
@Controller
public class ShareController {

    private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");

    private final TripService tripService;

    public ShareController(TripService tripService) {
        this.tripService = tripService;
    }

    /** GET /r/{token} → 200 공유 페이지, 404 없는 링크, 410 기간이 지난 링크 (모두 HTML). */
    @GetMapping("/r/{token}")
    public String share(@PathVariable String token, Model model, HttpServletResponse response) {
        // 링크를 연 페이지의 주소(토큰)가 이미지 서버 등 외부로 새지 않게 하고, 검색엔진에 올라가지 않게 한다.
        privacyHeaders(response);
        response.setHeader("Cache-Control", "no-store");

        SharePageResult result = tripService.findSharePage(token);
        switch (result) {
            case SharePageResult.Found found -> {
                model.addAttribute("page", found.view());
                return "share";
            }
            case SharePageResult.NotFound notFound -> {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                model.addAttribute("title", "링크를 찾을 수 없어요");
                model.addAttribute("message", "주소가 정확한지 확인해 주세요.");
                return "share-unavailable";
            }
            case SharePageResult.Expired expired -> {
                response.setStatus(HttpStatus.GONE.value());
                model.addAttribute("title", "공유 기간이 끝났어요");
                model.addAttribute("message", "이 드라이브 기록은 더 이상 볼 수 없어요.");
                return "share-unavailable";
            }
        }
    }

    /** GET /r/{token}/video.mp4 → 리캡 영상(F-07). Range 요청을 지원해 휴대폰 브라우저에서 바로 재생된다. */
    @GetMapping("/r/{token}/video.mp4")
    public ResponseEntity<Resource> video(@PathVariable String token, HttpServletResponse response) {
        return media(token, MediaKind.VIDEO, VIDEO_MP4, response);
    }

    /** GET /r/{token}/thumbnail.jpg → 리캡 썸네일(16:9, handoff 3-2). 앱 결과 화면·공유 페이지 영상 포스터. */
    @GetMapping("/r/{token}/thumbnail.jpg")
    public ResponseEntity<Resource> thumbnail(@PathVariable String token, HttpServletResponse response) {
        return media(token, MediaKind.THUMBNAIL, MediaType.IMAGE_JPEG, response);
    }

    private ResponseEntity<Resource> media(String token, MediaKind kind, MediaType type, HttpServletResponse response) {
        privacyHeaders(response);
        return switch (tripService.findMedia(token, kind)) {
            case MediaResult.Found found -> ResponseEntity.ok()
                    .contentType(type)
                    // 파일은 바뀌지 않지만 공유 기간이 끝나면 사라지므로 공용 캐시에는 두지 않는다.
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                    .body(new FileSystemResource(found.file()));
            case MediaResult.NotFound notFound -> ResponseEntity.notFound().build();
            case MediaResult.Expired expired -> ResponseEntity.status(HttpStatus.GONE).build();
        };
    }

    private static void privacyHeaders(HttpServletResponse response) {
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
    }
}
