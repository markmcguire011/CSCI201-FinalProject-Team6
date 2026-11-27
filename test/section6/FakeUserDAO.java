package section6;

import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.model.User;

import java.util.HashMap;
import java.util.Map;

/** A UserDAO that keeps users in a map instead of MySQL, and counts the calls the tests check. */
class FakeUserDAO extends UserDAO {
    final Map<Integer, User> users = new HashMap<>();
    int findByIdCalls;
    int updateCalls;
    String savedName, savedBio, savedPhotoPath;
    RuntimeException failUpdate;   // when set, updateProfile throws it instead of saving

    FakeUserDAO() {
        super(null);   // no database: every method the tests use is overridden
    }

    @Override
    public User findById(int userId) {
        findByIdCalls++;
        return users.get(userId);
    }

    @Override
    public void updateProfile(int userId, String fullName, String bio, String photoPath) {
        updateCalls++;
        if (failUpdate != null) throw failUpdate;
        savedName = fullName;
        savedBio = bio;
        savedPhotoPath = photoPath;
        User u = users.get(userId);
        u.setFullName(fullName);
        u.setBio(bio);
        u.setPhotoPath(photoPath);
    }
}
