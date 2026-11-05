package com.hbeu.campusguide.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hbeu.campusguide.domain.CampusMedia;
import com.hbeu.campusguide.dto.MediaRef;
import com.hbeu.campusguide.mapper.CampusMediaMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CampusMediaService {
    private final CampusMediaMapper mediaMapper;

    public CampusMediaService(CampusMediaMapper mediaMapper) {
        this.mediaMapper = mediaMapper;
    }

    public List<MediaRef> publishedForPoint(Long pointId) {
        if (pointId == null) return List.of();
        return mediaMapper.selectList(Wrappers.<CampusMedia>lambdaQuery()
                        .eq(CampusMedia::getPointId, pointId)
                        .eq(CampusMedia::getStatus, "PUBLISHED")
                        .orderByAsc(CampusMedia::getSortOrder))
                .stream()
                .map(media -> new MediaRef(
                        media.getId(),
                        media.getTitle(),
                        media.getFileUrl(),
                        media.getAltText(),
                        media.getSourceTitle(),
                        media.getSourceUrl()
                ))
                .toList();
    }
}
