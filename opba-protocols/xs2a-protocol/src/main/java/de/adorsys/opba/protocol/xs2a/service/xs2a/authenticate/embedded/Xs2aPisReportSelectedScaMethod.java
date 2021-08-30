package de.adorsys.opba.protocol.xs2a.service.xs2a.authenticate.embedded;

import de.adorsys.opba.protocol.bpmnshared.dto.DtoMapper;
import de.adorsys.opba.protocol.bpmnshared.service.context.ContextUtil;
import de.adorsys.opba.protocol.bpmnshared.service.exec.ValidatedExecution;
import de.adorsys.opba.protocol.xs2a.context.Xs2aContext;
import de.adorsys.opba.protocol.xs2a.context.pis.Xs2aPisContext;
import de.adorsys.opba.protocol.xs2a.service.dto.ValidatedPathHeadersBody;
import de.adorsys.opba.protocol.xs2a.service.mapper.PathHeadersBodyMapperTemplate;
import de.adorsys.opba.protocol.xs2a.service.xs2a.authenticate.ScaUtil;
import de.adorsys.opba.protocol.xs2a.service.xs2a.dto.Xs2aAuthorizedPaymentParameters;
import de.adorsys.opba.protocol.xs2a.service.xs2a.dto.Xs2aStandardHeaders;
import de.adorsys.opba.protocol.xs2a.service.xs2a.dto.authenticate.embedded.SelectScaChallengeBody;
import de.adorsys.opba.protocol.xs2a.service.xs2a.validation.Xs2aValidator;
import de.adorsys.opba.protocol.xs2a.util.logresolver.Xs2aLogResolver;
import de.adorsys.xs2a.adapter.api.PaymentInitiationService;
import de.adorsys.xs2a.adapter.api.RequestParams;
import de.adorsys.xs2a.adapter.api.Response;
import de.adorsys.xs2a.adapter.api.model.PaymentProduct;
import de.adorsys.xs2a.adapter.api.model.PaymentService;
import de.adorsys.xs2a.adapter.api.model.SelectPsuAuthenticationMethod;
import de.adorsys.xs2a.adapter.api.model.SelectPsuAuthenticationMethodResponse;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Service;

/**
 * Send the selected SCA method by the user to the ASPSP. The challenge is typically provided by
 * {@link de.adorsys.opba.protocol.api.authorization.UpdateAuthorization}.
 */
@Service("xs2aPisReportSelectedScaMethod")
@RequiredArgsConstructor
public class Xs2aPisReportSelectedScaMethod extends ValidatedExecution<Xs2aPisContext> {

    private static final String DECOUPLED_AUTHENTICATION_PSU_MESSAGE = "Please check your app to continue";

    private final Extractor extractor;
    private final Xs2aValidator validator;
    private final PaymentInitiationService pis;
    private final Xs2aLogResolver logResolver = new Xs2aLogResolver(getClass());

    @Override
    protected void doValidate(DelegateExecution execution, Xs2aPisContext context) {
        logResolver.log("doValidate: execution ({}) with context ({})", execution, context);

        validator.validate(execution, context, this.getClass(), extractor.forValidation(context));
    }

    @Override
    protected void doRealExecution(DelegateExecution execution, Xs2aPisContext context) {
        logResolver.log("doRealExecution: execution ({}) with context ({})", execution, context);

        ValidatedPathHeadersBody<Xs2aAuthorizedPaymentParameters, Xs2aStandardHeaders, SelectPsuAuthenticationMethod> params = extractor.forExecution(context);

        logResolver.log("updatePaymentPsuData with parameters: {}", params.getPath(), params.getHeaders(), params.getBody());

        Response<SelectPsuAuthenticationMethodResponse> authResponse = pis.updatePaymentPsuData(
                PaymentService.fromValue(params.getPath().getPaymentType().getValue()),
                PaymentProduct.fromValue(params.getPath().getPaymentProduct()),
                params.getPath().getPaymentId(),
                params.getPath().getAuthorizationId(),
                params.getHeaders().toHeaders(),
                RequestParams.empty(),
                params.getBody()
        );

        logResolver.log("updatePaymentPsuData response: {}", authResponse);

        ContextUtil.getAndUpdateContext(
                execution,
                (Xs2aContext ctx) -> {
                    ctx.setScaSelected(ScaUtil.scaMethodSelected(authResponse.getBody()));
                    ctx.setChallengeData(authResponse.getBody().getChallengeData());
                    ctx.setSelectedScaDecoupled(ScaUtil.isDecoupled(authResponse.getHeaders()));
                }
        );
    }

    @Service
    public static class Extractor extends PathHeadersBodyMapperTemplate<Xs2aPisContext,
                                                                               Xs2aAuthorizedPaymentParameters,
                                                                               Xs2aStandardHeaders,
                                                                               SelectScaChallengeBody,
                                                                               SelectPsuAuthenticationMethod> {

        public Extractor(
                DtoMapper<Xs2aContext, SelectScaChallengeBody> toValidatableBody,
                DtoMapper<SelectScaChallengeBody, SelectPsuAuthenticationMethod> toBody,
                DtoMapper<Xs2aContext, Xs2aStandardHeaders> toHeaders,
                DtoMapper<Xs2aPisContext, Xs2aAuthorizedPaymentParameters> toParameters) {
            super(toValidatableBody, toBody, toHeaders, toParameters);
        }
    }
}
