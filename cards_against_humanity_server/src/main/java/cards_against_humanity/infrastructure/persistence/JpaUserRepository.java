package cards_against_humanity.infrastructure.persistence;

import cards_against_humanity.domain.model.User;
import cards_against_humanity.domain.repository.UserRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;
import jakarta.persistence.NoResultException;
import java.util.List;
import java.util.Optional;

public class JpaUserRepository implements UserRepository {

    private final Transaction transaction;

    /**
     * Construtor padrão da persistência de Usuários, habilitando wrapper de Transaction.
     */
    public JpaUserRepository() {
        this.transaction = new Transaction();
    }

    /**
     * Garante o cadastramento ou a reescrita de dados atualizados de perfil
     * na tabela final referida no esquema do banco para o App.
     * Continua gerenciando (managed) após push DB.
     *
     * @param user Usuário (perfil cru recém preenchido).
     * @return O próprio perfil consolidado.
     */
    @Override
    public User save(User user) {
        transaction.executeVoid(em -> em.persist(user));
        return user;
    }

    /**
     * Captura um player unicamente através de seu Token de identificação primária.
     *
     * @param id Auto-increment / UUID.
     * @return Optional instanciado seguramente à prova do Null.
     */
    @Override
    public Optional<User> findById(String id) {
        return transaction.execute(em -> Optional.ofNullable(em.find(User.class, id)));
    }

    /**
     * Despeja da base inteira a Listagem unânime de cadastros de players persistidos desde a criação.
     *
     * @return Vetor de perfis.
     */
    @Override
    public List<User> findAll() {
        return transaction.execute(em ->
            em.createQuery("SELECT u FROM User u", User.class).getResultList()
        );
    }

    /**
     * Elimina do app perfis indesejados reassociando à sessão `manage` interna se necessário para dar detach e remover.
     *
     * @param user Um Objeto de tipo User completo.
     */
    @Override
    public void delete(User user) {
        transaction.executeVoid(em -> {
            User managed = em.contains(user) ? user : em.merge(user);
            em.remove(managed);
        });
    }

    /**
     * Variante conveniente ao Delete puro economizando carga de leitura extra.
     * Procura estritamente por ID antes de deletar via EntityManager.
     *
     * @param id String crua correspondente a PK de DB do user.
     */
    public void deleteById(String id) {
        transaction.executeVoid(em -> {
            User user = em.find(User.class, id);
            if (user != null) {
                em.remove(user);
            }
        });
    }   

    /**
     * Localiza perfis com base em correspondência exata para autenticações de Login.
     * Blinda as exceptions de "No Result" do JPA dentro de um try/catch, devolvendo null seguro.
     *
     * @param email O e-mail rastreado no DB.
     * @return Container Optional sem erros críticos.
     */
    @Override
    public Optional<User> findByEmail(String email) {
        return transaction.execute(em -> {
            try {
                return Optional.of(em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                        .setParameter("email", email)
                        .getSingleResult());
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Localiza perfis com base em correspondência cruzada única para validação na hora da criação de cadastros.
     *
     * @param username O username avaliado unicuamente.
     * @return O User empacotado Optional vazio.
     */
    @Override
    public Optional<User> findByUsername(String username) {
        return transaction.execute(em -> {
            try {
                return Optional.of(em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                        .setParameter("username", username)
                        .getSingleResult());
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Executa um query Count rápido avaliando se já houve fixação daquele e-mail na tabela de users.
     *
     * @param email A chave string de correio eletrônico do candidato.
     * @return Flag confirmando presença em database, > 0.
     */
    @Override
    public boolean existsByEmail(String email) {
        return transaction.execute(em -> {
            Long count = em.createQuery("SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                    .setParameter("email", email)
                    .getSingleResult();
            return count > 0;
        });
    }

    /**
     * Executa um query Count otimizado avaliando a existência de certo username nos perfis.
     *
     * @param username O nick do candidato sob análise.
     * @return true se em uso bloqueante; false se disponível pra inserção.
     */
    @Override
    public boolean existsByUsername(String username) {
        return transaction.execute(em -> {
            Long count = em.createQuery("SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                    .setParameter("username", username)
                    .getSingleResult();
            return count > 0;
        });
    }
}