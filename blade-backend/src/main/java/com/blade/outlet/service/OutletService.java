package com.blade.outlet.service;

import com.blade.common.result.PageResult;
import com.blade.outlet.dto.OutletCreateDTO;
import com.blade.outlet.dto.OutletOptionsVO;
import com.blade.outlet.dto.OutletPageDTO;
import com.blade.outlet.dto.OutletUpdateDTO;
import com.blade.outlet.dto.OutletVO;

import java.util.List;

public interface OutletService {

    PageResult<OutletVO> pageList(OutletPageDTO dto);

    OutletVO getById(Long id);

    Long create(OutletCreateDTO dto);

    void update(OutletUpdateDTO dto);

    void updateStatus(Long id, Integer status);

    OutletOptionsVO options();
}
