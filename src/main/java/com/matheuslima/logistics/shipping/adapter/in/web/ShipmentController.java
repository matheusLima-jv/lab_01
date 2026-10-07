package com.matheuslima.logistics.shipping.adapter.in.web;

import com.matheuslima.logistics.shared.application.PageQuery;
import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shared.infrastructure.web.PageResponse;
import com.matheuslima.logistics.shipping.application.port.in.ChangeShipmentStatusCommand;
import com.matheuslima.logistics.shipping.application.port.in.ChangeShipmentStatusUseCase;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentCommand;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentCommand.AddressInput;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentUseCase;
import com.matheuslima.logistics.shipping.application.port.in.GetShipmentUseCase;
import com.matheuslima.logistics.shipping.application.port.in.GetTrackingHistoryUseCase;
import com.matheuslima.logistics.shipping.application.port.in.ListShipmentsQuery;
import com.matheuslima.logistics.shipping.application.port.in.ListShipmentsUseCase;
import com.matheuslima.logistics.shipping.application.port.in.ShipmentView;
import com.matheuslima.logistics.shipping.application.port.in.StatusChange;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * <b>Adaptador de entrada REST</b> do recurso "envios".
 *
 * <h2>Desenho dos endpoints</h2>
 * <pre>
 * POST   /api/v1/shipments                 cria um envio                      201 + Location
 * GET    /api/v1/shipments                 lista (paginada, filtro por status) 200
 * GET    /api/v1/shipments/{id}            consulta um envio                   200 | 404
 * POST   /api/v1/shipments/{id}/dispatch   despacha (operador)                 200 | 403 | 404 | 409
 * POST   /api/v1/shipments/{id}/deliver    registra a entrega (operador)       200 | 403 | 404 | 409
 * POST   /api/v1/shipments/{id}/cancel     cancela (dono ou operador)          200 | 404 | 409
 * GET    /api/v1/shipments/{id}/tracking   histórico de rastreamento           200 | 404
 * </pre>
 *
 * <h2>Transições como "controller resources" ({@code POST /{id}/cancel})</h2>
 * Mudar o status de um envio não é uma atualização de campo qualquer: cada transição tem regras,
 * dados próprios (o cancelamento tem motivo) e efeitos (eventos). As alternativas mais comuns:
 * <ul>
 *   <li>{@code PATCH /shipments/{id}} com {@code {"status": "CANCELLED"}}: esconde a intenção
 *       atrás de uma edição genérica e permite "pular" estados ({@code CREATED → DELIVERED}).</li>
 *   <li>{@code PUT /shipments/{id}/status}: melhor, mas ainda trata a ação como dado.</li>
 *   <li>{@code POST /shipments/{id}/cancel}: o arquétipo <i>controller</i> do "REST API Design
 *       Rulebook" (Mark Massé), para ações que não se encaixam em CRUD. É também o "custom
 *       method" das guidelines de API do Google ({@code POST /shipments/123:cancel}).</li>
 * </ul>
 * Escolhemos a última: a URL fala a linguagem do domínio e cada ação mapeia 1:1 para um método do
 * agregado ({@code shipment.cancel(...)}). Usamos POST porque as ações não são idempotentes:
 * repetir um despacho gera 409.
 *
 * <h2>Nível 2 de maturidade de Richardson</h2>
 * Recursos + verbos HTTP + status codes corretos. O nível 3 (HATEOAS, links na resposta) é pouco
 * usado na prática e fica documentado como evolução.
 */
@RestController
@RequestMapping("/api/v1/shipments")
@Tag(name = "Envios", description = "Criação, consulta, ciclo de vida e rastreamento de envios")
public class ShipmentController {

    private final CreateShipmentUseCase createShipment;
    private final ChangeShipmentStatusUseCase changeShipmentStatus;
    private final GetShipmentUseCase getShipment;
    private final ListShipmentsUseCase listShipments;
    private final GetTrackingHistoryUseCase getTrackingHistory;

    public ShipmentController(CreateShipmentUseCase createShipment, ChangeShipmentStatusUseCase changeShipmentStatus,
                              GetShipmentUseCase getShipment, ListShipmentsUseCase listShipments,
                              GetTrackingHistoryUseCase getTrackingHistory) {
        this.createShipment = createShipment;
        this.changeShipmentStatus = changeShipmentStatus;
        this.getShipment = getShipment;
        this.listShipments = listShipments;
        this.getTrackingHistory = getTrackingHistory;
    }

    @PostMapping
    @Operation(summary = "Cria um envio",
            description = "Consulta os CEPs na ViaCEP, calcula o frete e registra o envio para quem chama.")
    @ApiResponse(responseCode = "201", description = "Envio criado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "422", description = "CEP inexistente")
    @ApiResponse(responseCode = "503", description = "Serviço de CEP indisponível (tente novamente)")
    public ResponseEntity<ShipmentResponse> create(@Valid @RequestBody CreateShipmentRequest request,
                                                   Requester requester) {
        ShipmentView created = createShipment.create(new CreateShipmentCommand(
                requester,
                toInput(request.origin()),
                toInput(request.destination()),
                request.weightGrams()));
        URI location = UriComponentsBuilder.fromPath("/api/v1/shipments/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(ShipmentResponse.from(created));
    }

    @GetMapping
    @Operation(summary = "Lista envios",
            description = "Clientes veem os próprios envios; operadores veem todos. Mais recentes primeiro.")
    @ApiResponse(responseCode = "200", description = "Página de envios")
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido (status desconhecido, size fora de 1 a 100)")
    public PageResponse<ShipmentResponse> list(
            @Parameter(description = "Filtra por status") @RequestParam(required = false) ShipmentStatus status,
            @Parameter(description = "Página, a partir de 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Itens por página (1 a 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size,
            Requester requester) {
        return PageResponse.from(
                listShipments.list(new ListShipmentsQuery(requester, status, new PageQuery(page, size))),
                ShipmentResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um envio")
    @ApiResponse(responseCode = "200", description = "Envio encontrado")
    @ApiResponse(responseCode = "404", description = "Inexistente ou de outro cliente")
    public ShipmentResponse get(@PathVariable UUID id, Requester requester) {
        return ShipmentResponse.from(getShipment.get(requester, id));
    }

    /**
     * {@code @PreAuthorize} rejeita clientes antes de qualquer acesso ao banco e documenta a
     * exigência. O caso de uso verifica o papel de novo (defesa em profundidade).
     */
    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Despacha um envio (operadores)", description = "CREATED → IN_TRANSIT")
    @ApiResponse(responseCode = "200", description = "Envio despachado")
    @ApiResponse(responseCode = "403", description = "Quem chama não é operador")
    @ApiResponse(responseCode = "404", description = "Envio inexistente")
    @ApiResponse(responseCode = "409", description = "Transição inválida para o status atual")
    public ShipmentResponse dispatch(@PathVariable UUID id, Requester requester) {
        return changeStatus(requester, id, new StatusChange.Dispatch());
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Registra a entrega (operadores)", description = "IN_TRANSIT → DELIVERED")
    @ApiResponse(responseCode = "200", description = "Entrega registrada")
    @ApiResponse(responseCode = "403", description = "Quem chama não é operador")
    @ApiResponse(responseCode = "404", description = "Envio inexistente")
    @ApiResponse(responseCode = "409", description = "Transição inválida para o status atual")
    public ShipmentResponse deliver(@PathVariable UUID id, Requester requester) {
        return changeStatus(requester, id, new StatusChange.Deliver());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancela um envio", description = "CREATED → CANCELLED. Dono do envio ou operador.")
    @ApiResponse(responseCode = "200", description = "Envio cancelado")
    @ApiResponse(responseCode = "400", description = "Motivo ausente ou longo demais")
    @ApiResponse(responseCode = "404", description = "Inexistente ou de outro cliente")
    @ApiResponse(responseCode = "409", description = "Envio já despachado, entregue ou cancelado")
    public ShipmentResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelShipmentRequest request,
                                   Requester requester) {
        return changeStatus(requester, id, new StatusChange.Cancel(request.reason()));
    }

    @GetMapping("/{id}/tracking")
    @Operation(summary = "Histórico de rastreamento",
            description = "Linha do tempo do envio. Consistência eventual: pode levar alguns instantes para "
                    + "refletir uma mudança recente.")
    @ApiResponse(responseCode = "200", description = "Linha do tempo, do evento mais antigo ao mais recente")
    @ApiResponse(responseCode = "404", description = "Inexistente ou de outro cliente")
    public TrackingHistoryResponse tracking(@PathVariable UUID id, Requester requester) {
        return TrackingHistoryResponse.from(id, getTrackingHistory.history(requester, id));
    }

    private ShipmentResponse changeStatus(Requester requester, UUID id, StatusChange change) {
        return ShipmentResponse.from(changeShipmentStatus.change(new ChangeShipmentStatusCommand(requester, id, change)));
    }

    private static AddressInput toInput(CreateShipmentRequest.AddressRequest address) {
        return new AddressInput(address.postalCode(), address.number(), address.complement());
    }
}
