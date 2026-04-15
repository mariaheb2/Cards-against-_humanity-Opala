USE cards_db;

-- Cartas pergunta (QUESTION)
INSERT INTO cards (id, text, type) VALUES
  (UUID(), 'Por que cheguei atrasado: ___', 'QUESTION'),
  (UUID(), '___ é a razão pela qual choro no chuveiro.', 'QUESTION'),
  (UUID(), 'O que me mantém acordado à noite: ___', 'QUESTION'),
  (UUID(), 'Segundo os cientistas, ___ é a causa do aquecimento global.', 'QUESTION'),
  (UUID(), 'Meu plano de aposentadoria: ___', 'QUESTION');

-- Cartas resposta (ANSWER)
INSERT INTO cards (id, text, type) VALUES
  (UUID(), 'Uma batata quente', 'ANSWER'),
  (UUID(), 'Morgan Freeman narrando minha vida', 'ANSWER'),
  (UUID(), 'O Wi-Fi caindo na hora errada', 'ANSWER'),
  (UUID(), 'Aquela sensação de déjà vu', 'ANSWER'),
  (UUID(), 'Um cachorro usando óculos', 'ANSWER'),
  (UUID(), 'Acusar o estagiário', 'ANSWER'),
  (UUID(), 'Ovos mexidos às 3 da manhã', 'ANSWER'),
  (UUID(), 'Um complô do governo', 'ANSWER'),
  (UUID(), 'Cheiro de gasolina', 'ANSWER'),
  (UUID(), 'Nicolas Cage em seu melhor momento', 'ANSWER');
