package com.ferrocompras.ordenescompra.moduloadministracion.service;

import com.ferrocompras.ordenescompra.dto.CategoriaResponse;
import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.mapper.CategoriaMapper;
import com.ferrocompras.ordenescompra.shared.repository.CategoriaProductoRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoriaService {

    private final CategoriaProductoRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;

    public CategoriaService(CategoriaProductoRepository categoriaRepository, CategoriaMapper categoriaMapper) {
        this.categoriaRepository = categoriaRepository;
        this.categoriaMapper = categoriaMapper;
    }

    public ListEnvelope<CategoriaResponse> listar() {
        List<CategoriaResponse> data = categoriaRepository.findAll().stream().map(categoriaMapper::toResponse).toList();
        return ListEnvelope.of(data, false);
    }
}
