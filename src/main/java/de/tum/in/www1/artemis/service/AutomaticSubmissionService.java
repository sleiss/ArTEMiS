package de.tum.in.www1.artemis.service;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class AutomaticSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticSubmissionService.class);

    private static Map<Long, Map<String, Submission>> submissionHashMap = new ConcurrentHashMap<>();

    private static ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    static {
        threadPoolTaskScheduler.setThreadNamePrefix("AutomaticSubmission");
        threadPoolTaskScheduler.setPoolSize(3);
        threadPoolTaskScheduler.initialize();
    }

    private ScheduledFuture scheduledFuture;

    private final ExerciseService exerciseService;
    private final ParticipationRepository participationRepository;
    private final ResultRepository resultRepository;
    private final ModelingSubmissionService modelingSubmissionService;
    private final ModelingSubmissionRepository modelingSubmissionRepository;

    public AutomaticSubmissionService(ExerciseService exerciseService,
                                      ParticipationRepository participationRepository,
                                      ResultRepository resultRepository,
                                      ModelingSubmissionService modelingSubmissionService,
                                      ModelingSubmissionRepository modelingSubmissionRepository) {
        this.exerciseService = exerciseService;
        this.participationRepository = participationRepository;
        this.resultRepository = resultRepository;
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
    }

    /**
     * start scheduler
     */
    public void startSchedule(long delayInMillis) {
        log.info("AutomaticSubmissionService was started to run repeatedly with {} second gaps.", delayInMillis / 1000.0);
        scheduledFuture = threadPoolTaskScheduler.scheduleWithFixedDelay(this::run, delayInMillis);
    }

    /**
     * stop scheduler (doesn't interrupt if running)
     */
    public void stopSchedule() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    /**
     * add a submission to the submissionHashMap
     *
     * @param exerciseId     the exerciseId of the quiz the submission belongs to (first Key)
     * @param username       the username of the user, who submitted the submission (second Key)
     * @param submission the submission, which should be added (Value)
     */
    public static void updateSubmission(Long exerciseId, String username, Submission submission) {

        if (submission != null && exerciseId != null && username != null) {
            // check if there is already a submission with the same exercise
            if (!submissionHashMap.containsKey(exerciseId)) {
                submissionHashMap.put(exerciseId, new ConcurrentHashMap<>());
            }
            submissionHashMap.get(exerciseId).put(username, submission);
        }
    }

    private void run() {
        // global try-catch for error logging
        try {
            long start = System.currentTimeMillis();

            // update Participations if the submission was submitted or if the exercise has ended and save them to Database (DB Write)
            for (long exerciseId : submissionHashMap.keySet()) {

                Exercise exercise = exerciseService.findOne(exerciseId);
                // check if quiz has been deleted
                if (exercise == null) {
                    submissionHashMap.remove(exerciseId);
                    continue;
                }

                // if exercise has ended, all submissions will be processed => we can remove the inner HashMap for this quiz
                // if exercise hasn't ended, some submissions (those that are not submitted) will stay in HashMap => keep inner HashMap
                Map<String, Submission> submissions;
                if (exercise.isEnded()) {
                    log.debug("exercise {} ended", exercise.getId());
                    submissions = submissionHashMap.remove(exerciseId);
                } else {
                    submissions = submissionHashMap.get(exerciseId);
                }

                int num = handleSubmissions(exercise, submissions);

                log.info("Processed {} submissions after {} ms in exercise {}", num, System.currentTimeMillis() - start, exercise.getTitle());
            }
        } catch (Exception e) {
            log.error("Exception in AutomaticSubmissionService:\n{}", e.getMessage());
        }
    }

    /**
     * check if the user submitted the submission or if the exercise has ended:
     * if true: -> Update Participation and save to Database (DB Write)
     * Remove processed Submissions from SubmissionHashMap
     *
     * @param exercise          the exercise which should be checked
     * @param userSubmissionMap a Map with all submissions for the given exercise mapped by the username
     * @return the number of updated participations
     */
    private int handleSubmissions(Exercise exercise, Map<String, Submission> userSubmissionMap) {
        int counter = 0;

        for (String username : userSubmissionMap.keySet()) {
            try {
                // first case: the user submitted the quizSubmission
                if (userSubmissionMap.get(username).isSubmitted()) {
                    Submission submission = userSubmissionMap.remove(username);
                    log.debug("user submitted model {}", submission.getId());
                    if (submission.getType() == null) {
                        submission.setType(SubmissionType.MANUAL);
                    }

                    // Update Participation and save to Database (DB Write)
                    // Remove processed Submissions from SubmissionHashMap
                    if (updateParticipation(submission)) {
                        counter++;
                    }
                    // second case: the quiz has ended
                } else if (exercise.isEnded()) {
                    Submission submission = userSubmissionMap.remove(username);
                    log.debug("exercise ended for submission {}", submission.getId());
                    submission.setSubmitted(true);
                    submission.setType(SubmissionType.TIMEOUT);
                    submission.setSubmissionDate(ZonedDateTime.now());

                    // Update Participation and save to Database (DB Write)
                    // Remove processed Submissions from SubmissionHashMap
                    if (updateParticipation(submission)) {
                        counter++;
                    }
                }
            } catch (Exception e) {
                log.error("Exception in handleSubmissions() for {} in exercise {}:\n{}", username, exercise.getId(), e.getMessage());
            }
        }

        return counter;
    }

    /**
     * Updates the participation for a given submission.
     * The participation is set to FINISHED.
     *
     * @param submission    the submission for which the participation should be updated for
     * @return whether updating the participation for the submission was successful or not
     */
    private Boolean updateParticipation(Submission submission) {
        if (submission != null) {
            Optional<Result> optionalResult = resultRepository.findDistinctBySubmissionId(submission.getId());
            if (optionalResult.isPresent()) {
                Result result = optionalResult.get();
                Participation participation = result.getParticipation();
                if (participation != null) {
                    participation.setInitializationState(ParticipationState.FINISHED);
                    participationRepository.save(participation);
                } else {
                    log.error("The result {} found for submission {} has no participation.", result.getId(), submission.getId());
                    return false;
                }

                if (submission instanceof ModelingSubmission) {
                    // manually trigger submit for modelingSubmission to update compass
                    ModelingSubmission modelingSubmission = (ModelingSubmission) submission;
                    Exercise exercise = participation.getExercise();
                    if (modelingSubmission.getModel() == "") {
                        try {
                            JsonObject model = modelingSubmissionService.getModel(exercise.getId(), participation.getStudent().getId(), modelingSubmission.getId());
                            modelingSubmission.setModel(model.toString());
                        } catch (Exception e) {
                            log.error("Exception while retrieving the model for submission {}:\n{}", submission.getId(), e.getMessage());
                            return false;
                        }
                    }
                    if (exercise instanceof ModelingExercise) {
                        modelingSubmissionRepository.save(modelingSubmission);
                        modelingSubmissionService.submit(modelingSubmission, (ModelingExercise) exercise);
                    } else {
                        log.error("The exercise {} belonging to submission {} is not a ModelingExercise.", exercise.getId(), submission.getId());
                        return false;
                    }
                }

                return true;
            } else {
                log.error("No result could be found for submission {}.", submission.getId());
            }
        }
        log.error("Updating the participation for submission {} failed.", submission.getId());
        return false;
    }
}