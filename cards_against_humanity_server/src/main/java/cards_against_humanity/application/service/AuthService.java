package cards_against_humanity.application.service;

import cards_against_humanity.domain.model.User;
import cards_against_humanity.domain.repository.UserRepository;
import cards_against_humanity.domain.service.auth.PasswordEncoder;
import cards_against_humanity.network.dto.LoginRequest;
import cards_against_humanity.network.dto.RegisterRequest;

import java.util.Optional;

public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registra um novo usuário.
     * @return userId se sucesso, ou lança exceção com mensagem amigável.
     */
    public String register(RegisterRequest request) {
        // Verifica se email já existe
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered diva, login with your email or choose another one");
        }
        // Verifica se username já existe
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken diva, please choose another one, kisses");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getUsername(), request.getEmail(), hashedPassword);
        userRepository.save(user);
        return user.getId();
    }

    public boolean validateUserById(String userId, String username) {
        Optional<User> user = userRepository.findById(userId);
        return user.isPresent() && user.get().getUsername().equals(username);
    }

    /**
     * Realiza login.
     * @return User se credenciais válidas, caso contrário lança exceção.
     */
    public User login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return user;
    }
}