package com.ExpenseTracker.app.wishlist.mapper;

import com.ExpenseTracker.app.wishlist.persistence.entity.WishlistEntity;
import com.ExpenseTracker.app.wishlist.presentation.dto.CreateWishlistDTO;
import com.ExpenseTracker.app.wishlist.presentation.dto.UpdateWishlistDTO;
import com.ExpenseTracker.app.wishlist.presentation.dto.WishlistDTO;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface WishlistMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(target = "progressPercentage", ignore = true)
    WishlistDTO toDTO(WishlistEntity entity);

    @AfterMapping
    default void computeProgress(@MappingTarget WishlistDTO dto, WishlistEntity entity) {
        BigDecimal target = entity.getTargetAmount();
        BigDecimal current = entity.getCurrentAmount();
        if (target != null && target.compareTo(BigDecimal.ZERO) > 0 && current != null) {
            dto.setProgressPercentage(
                    current.divide(target, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP)
            );
        } else {
            dto.setProgressPercentage(BigDecimal.ZERO);
        }
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "currentAmount", ignore = true)
    @Mapping(target = "status", ignore = true)
    WishlistEntity toEntity(CreateWishlistDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updateEntityFromDTO(UpdateWishlistDTO dto, @MappingTarget WishlistEntity entity);
}
