package com.vet_saas.modules.newsletter.service;

import com.vet_saas.modules.newsletter.model.NewsletterSubscriber;
import com.vet_saas.modules.newsletter.repository.NewsletterSubscriberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NewsletterService {

    private final NewsletterSubscriberRepository repository;

    public void subscribe(String email) {
        repository.findByEmail(email).ifPresentOrElse(
                existing -> {
                    if (!existing.isActivo()) {
                        existing.setActivo(true);
                        repository.save(existing);
                    }
                },
                () -> repository.save(NewsletterSubscriber.builder().email(email).build())
        );
    }
}
