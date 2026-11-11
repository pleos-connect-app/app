package com.randomdrive.server.controller;

import com.randomdrive.server.service.TripService;
import com.randomdrive.server.service.TripService.SharePageResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 공유 페이지 (F-08). 차량 화면의 QR을 휴대폰으로 찍으면 열리는 모바일 웹 페이지.
 * JSON API가 아니라 HTML을 돌려주므로 @RestController가 아니다.
 */
@Controller
public class ShareController {

    private final TripService tripService;

    public ShareController(TripService tripService) {
        this.tripService = tripService;
    }

    /** GET /r/{token} → 200 공유 페이지, 404 없는 링크, 410 기간이 지난 링크 (모두 HTML). */
    @GetMapping("/r/{token}")
    public String share(@PathVariable String token, Model model, HttpServletResponse response) {
        // 링크를 연 페이지의 주소(토큰)가 이미지 서버 등 외부로 새지 않게 하고, 검색엔진에 올라가지 않게 한다.
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
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
}
