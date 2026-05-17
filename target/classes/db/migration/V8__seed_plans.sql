INSERT INTO plans (name, slug, price_usd_monthly, max_knowledge_bases, max_documents_per_kb, max_messages_per_month, max_members, features, is_active)
VALUES
    ('Free',     'free',     0,   1,  5,      100,     1,  ARRAY['1 chatbot', '5 docs/KB', '100 msgs/mo'],                            true),
    ('Starter',  'starter',  29,  3,  50,     1000,    3,  ARRAY['3 chatbots', '50 docs/KB', '1k msgs/mo', 'Email support'],          true),
    ('Pro',      'pro',      99,  10, 500,    10000,   10, ARRAY['10 chatbots', '500 docs/KB', '10k msgs/mo', 'Priority support'],    true),
    ('Business', 'business', 299, -1, -1,     100000,  -1, ARRAY['Unlimited chatbots', 'Unlimited docs', '100k msgs/mo', 'SLA 99.9%'], true);
