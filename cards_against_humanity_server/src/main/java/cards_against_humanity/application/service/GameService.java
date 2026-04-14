package cards_against_humanity.application.service;

import cards_against_humanity.domain.model.*;
import cards_against_humanity.domain.model.enums.CardType;
import cards_against_humanity.domain.model.enums.GameState;
import cards_against_humanity.domain.repository.*;
import cards_against_humanity.infrastructure.transaction.Transaction;

import java.util.List;

/**
 * Serviço que orquestra o fluxo principal de uma partida.
 * <p>
 * Responsabilidades:
 * <ul>
 *   <li>Iniciar novas rodadas (sorteia carta pergunta, define juiz, distribui cartas)</li>
 *   <li>Registrar as cartas jogadas pelos participantes</li>
 *   <li>Processar a escolha do vencedor pelo juiz e atualizar pontuação</li>
 *   <li>Controlar o fim da rodada e transição para a próxima</li>
 * </ul>
 *
 * <p>Todas as operações são transacionais e assumem que o estado do jogo foi validado
 * previamente (recomenda-se usar {@link GameStateService} para validação).
 *
 * @see LobbyService
 * @see GameStateService
 */
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final CardRepository cardRepository;
    private final PlayedCardRepository playedCardRepository;
    private final Transaction transaction;

    /**
     * Constrói um GameService com os repositórios necessários.
     *
     * @param gameRepository       repositório de partidas
     * @param playerRepository     repositório de jogadores
     * @param cardRepository       repositório de cartas
     * @param playedCardRepository repositório de cartas jogadas
     */
    public GameService(GameRepository gameRepository,
                       PlayerRepository playerRepository,
                       CardRepository cardRepository,
                       PlayedCardRepository playedCardRepository) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.cardRepository = cardRepository;
        this.playedCardRepository = playedCardRepository;
        this.transaction = new Transaction();
    }

    /**
     * Inicia uma nova rodada dentro de um jogo já em andamento.
     *
     * <p>Ações executadas:
     * <ol>
     *   <li>Incrementa o contador de rodadas</li>
     *   <li>Define o próximo juiz (ordem circular, ou aleatório na primeira rodada)</li>
     *   <li>Sorteia uma carta pergunta ({@code CardType.QUESTION})</li>
     *   <li>Garante que todos os jogadores tenham exatamente 5 cartas resposta na mão</li>
     *   <li>Altera o estado do jogo para {@code PLAYERS_PLAYING}</li>
     * </ol>
     *
     * @param gameId ID da partida
     * @return a carta pergunta sorteada para esta rodada
     * @throws IllegalArgumentException se a partida não existir
     * @throws IllegalStateException    se o estado atual não permitir nova rodada
     *                                  (deve ser {@code STARTING_GAME} ou {@code ROUND_FINISHED})
     * @throws IllegalStateException    se não houver cartas pergunta disponíveis no baralho
     */
    public Card startNewRound(String gameId) {
        return transaction.execute(em -> {
            Game game = em.find(Game.class, gameId);
            if (game == null) throw new IllegalArgumentException("Game not found");

            if (game.getState() != GameState.STARTING &&
                game.getState() != GameState.ROUND_FINISHED) {
                throw new IllegalStateException("Cannot start new round in current state: " + game.getState());
            }

            // Incrementa round
            game.setRound(game.getRound() + 1);
            int currentRound = game.getRound();

            // Define novo juiz (ordem circular)
            Player newJudge = getNextJudge(game);
            game.setCurrentJudge(newJudge);

            // Sorteia uma carta pergunta (QUESTION)
            List<Card> questions = cardRepository.findRandomQuestions(1);
            if (questions.isEmpty()) {
                throw new IllegalStateException("No question cards available");
            }
            Card questionCard = questions.get(0);
            game.setCurrentQuestion(questionCard);

            // Garante que cada jogador tenha 5 cartas na mão
            for (Player player : game.getPlayers()) {
                refillHand(player);
                playerRepository.save(player);
            }

            // Muda estado para PLAYERS_PLAYING (os jogadores podem começar a jogar)
            game.setState(GameState.PLAYERS_PLAYING);
            gameRepository.save(game);

            return questionCard;
        });
    }

        /**
     * Registra a jogada de um jogador (carta resposta escolhida).
     *
     * <p>Requisitos:
     * <ul>
     *   <li>O jogo deve estar no estado {@code PLAYERS_PLAYING}</li>
     *   <li>O jogador não pode ser o juiz da rodada</li>
     *   <li>O jogador não pode ter jogado nesta rodada ainda</li>
     *   <li>A carta deve pertencer à mão do jogador</li>
     * </ul>
     *
     * <p>Após registrar a jogada, a carta é removida da mão e uma nova carta resposta é
     * adicionada (mantendo 5 cartas). Se todos os não-juízes tiverem jogado, o estado
     * do jogo é alterado para {@code JUDGE_SELECTING}.
     *
     * @param gameId   ID da partida
     * @param playerId ID do jogador que está jogando
     * @param cardId   ID da carta resposta sendo jogada
     * @throws IllegalArgumentException se o jogo, jogador ou carta não existirem,
     *                                  ou se a carta não estiver na mão do jogador
     * @throws IllegalStateException    se o estado do jogo for inválido,
     *                                  ou se o jogador for o juiz,
     *                                  ou se o jogador já tiver jogado nesta rodada
     */
    public void playCard(String gameId, String playerId, String cardId) {
        transaction.executeVoid(em -> {
            Game game = em.find(Game.class, gameId);
            if (game == null) throw new IllegalArgumentException("Game not found");

            if (game.getState() != GameState.PLAYERS_PLAYING) {
                throw new IllegalStateException("Cannot play card now. Game state: " + game.getState());
            }

            Player player = em.find(Player.class, playerId);
            if (player == null) throw new IllegalArgumentException("Player not found");

            if (player.isJudge()) {
                throw new IllegalStateException("Judge cannot play a card");
            }

            // Verifica se o jogador já jogou nesta rodada
            boolean alreadyPlayed = playedCardRepository.findByGameId(gameId).stream()
                    .anyMatch(pc -> pc.getPlayer().getId().equals(playerId) && pc.getRoundNumber() == game.getRound());
            if (alreadyPlayed) {
                throw new IllegalStateException("Player already played this round");
            }

            // Verifica se a carta está na mão do jogador
            Card card = player.getHand().stream()
                    .filter(c -> c.getId().equals(cardId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Card not in player's hand"));

            // Cria PlayedCard
            PlayedCard playedCard = new PlayedCard(player, card, game);
            playedCard.setRoundNumber(game.getRound());
            playedCardRepository.save(playedCard);

            // Remove a carta da mão e adiciona uma nova
            player.removeCardFromHand(card);
            Card newCard = drawRandomAnswerCard();
            player.addCardToHand(newCard);
            playerRepository.save(player);

            // Verifica se todos os não-juízes já jogaram
            long nonJudges = game.getPlayers().stream().filter(p -> !p.isJudge()).count();
            long playedCount = playedCardRepository.findByGameId(gameId).stream()
                    .filter(pc -> pc.getRoundNumber() == game.getRound())
                    .count();

            if (playedCount == nonJudges) {
                game.setState(GameState.JUDGE_SELECTING);
                gameRepository.save(game);
            }
        });
    }

    /**
     * O juiz da rodada seleciona a carta vencedora entre as jogadas registradas.
     *
     * <p>Ações:
     * <ol>
     *   <li>Marca a {@link PlayedCard} escolhida como vencedora</li>
     *   <li>Adiciona 1 ponto ao jogador dono da carta</li>
     *   <li>Verifica se esse jogador atingiu a pontuação alvo ({@code targetScore})</li>
     *   <li>Se o jogo terminou, estado muda para {@code GAME_FINISHED}</li>
     *   <li>Caso contrário, estado muda para {@code ROUND_FINISHED} e chama {@link #endRound(String)}</li>
     * </ol>
     *
     * @param gameId       ID da partida
     * @param playedCardId ID da {@link PlayedCard} vencedora
     * @return o {@link Player} que venceu a rodada
     * @throws IllegalArgumentException se a partida ou a carta jogada não existirem,
     *                                  ou se a carta não pertencer ao jogo informado
     * @throws IllegalStateException    se o jogo não estiver no estado {@code JUDGE_SELECTING}
     */
    public Player selectWinner(String gameId, String playedCardId) {
        return transaction.execute(em -> {
            Game game = em.find(Game.class, gameId);
            if (game == null) throw new IllegalArgumentException("Game not found");

            if (game.getState() != GameState.JUDGE_SELECTING) {
                throw new IllegalStateException("Cannot select winner now");
            }

            PlayedCard winningCard = em.find(PlayedCard.class, playedCardId);
            if (winningCard == null) throw new IllegalArgumentException("PlayedCard not found");

            if (!winningCard.getGame().getId().equals(gameId)) {
                throw new IllegalArgumentException("Card does not belong to this game");
            }

            // Marca como vencedor
            winningCard.setWinner(true);
            playedCardRepository.save(winningCard);

            Player winnerPlayer = winningCard.getPlayer();
            winnerPlayer.addPoints(1);
            playerRepository.save(winnerPlayer);

            // Verifica se atingiu pontuação alvo
            if (winnerPlayer.getScore() >= game.getTargetScore()) {
                game.setState(GameState.GAME_FINISHED);
            } else {
                game.setState(GameState.ROUND_FINISHED);
            }
            return winnerPlayer;
        });
    }



    /**
     * Garante que um jogador tenha exatamente 5 cartas resposta na mão.
     * Se necessário, compra cartas aleatórias do baralho.
     *
     * @param player o jogador a ter a mão reabastecida
     * @throws IllegalStateException se não houver cartas resposta suficientes no baralho
     */
    private void refillHand(Player player) {
        int needed = 5 - player.getHand().size();
        if (needed > 0) {
            List<Card> newCards = cardRepository.findRandomAnswers(needed);
            for (Card card : newCards) {
                player.addCardToHand(card);
            }
        }
    }

    /**
     * Compra uma única carta resposta aleatória do baralho.
     *
     * @return uma carta do tipo {@code CardType.ANSWER}
     * @throws IllegalStateException se não houver cartas resposta disponíveis
     */
    private Card drawRandomAnswerCard() {
        List<Card> cards = cardRepository.findRandomAnswers(1);
        if (cards.isEmpty()) {
            throw new IllegalStateException("No answer cards available");
        }
        return cards.get(0);
    }

    /**
     * Determina qual será o próximo juiz da rodada.
     * <ul>
     *   <li>Se for a primeira rodada (nenhum juiz definido), escolhe aleatoriamente.</li>
     *   <li>Senão, avança para o próximo jogador na ordem circular.</li>
     * </ul>
     *
     * <p>Também atualiza o atributo {@code isJudge} de todos os jogadores.
     *
     * @param game a partida atual
     * @return o próximo jogador que será juiz
     * @throws IllegalStateException se a lista de jogadores estiver vazia
     */
    private Player getNextJudge(Game game) {
        List<Player> players = game.getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("No players in game");
        }
        // Atualiza o atributo isJudge de todos
        players.forEach(p -> p.setJudge(false));

        Player currentJudge = game.getCurrentJudge();
        Player nextJudge;
        
        if (currentJudge == null) {
            // Primeira rodada: juiz aleatório
            int randomIndex = (int) (Math.random() * players.size());
            nextJudge = players.get(randomIndex);
        } else {
            // Ordem circular
            int currentIndex = players.indexOf(currentJudge);
            int nextIndex = (currentIndex + 1) % players.size();
            nextJudge = players.get(nextIndex);
        }
        
        nextJudge.setJudge(true);
        return nextJudge;
    }
}